package kotlinx.atomicfu.transformer

import org.mozilla.javascript.*
import org.mozilla.javascript.ast.*
import java.io.File
import java.io.FileReader
import org.mozilla.javascript.Token

private const val ATOMIC_CONSTRUCTOR = """atomic\$(ref|int|long|boolean)\$"""
private const val ATOMIC_ARRAY_CONSTRUCTOR = """Atomic(Ref|Int|Long|Boolean)Array\$(ref|int|long|boolean)"""
private const val MANGLED_VALUE_PROP = "kotlinx\$atomicfu\$value"
private const val RECEIVER = "\$receiver"
private const val SCOPE = "scope"
private const val FACTORY = "factory"
private const val REQUIRE = "require"
private const val KOTLINX_ATOMICFU = "'kotlinx-atomicfu'"
private const val ARRAY = "Array"
private const val FILL = "fill"
private const val GET_ELEMENT = "get\$atomicfu"

class AtomicFUTransformerJS(
    inputDir: File,
    outputDir: File,
    var requireKotlinxAtomicfu: Boolean = false
) : AtomicFUTransformerBase(inputDir, outputDir) {
    private val atomicConstructors = mutableSetOf<String>()
    private val atomicArrayConstructors = mutableMapOf<String, String?>()

    override fun transform() {
        info("Transforming to $outputDir")
        inputDir.walk().filter { it.isFile }.forEach { file ->
            val outBytes = if (file.isJsFile()) {
                println("Transforming file: ${file.canonicalPath}")
                transformFile(file)
            } else {
                file.readBytes()
            }
            file.toOutputFile().mkdirsAndWrite(outBytes)
        }
    }

    private fun File.isJsFile() =
        name.endsWith(".js") && !name.endsWith(".meta.js")

    private fun transformFile(file: File): ByteArray {
        val p = Parser(CompilerEnvirons())
        val root = p.parse(FileReader(file), null, 0)
        root.visit(DependencyEraser())
        root.visit(AtomicConstructorDetector())
        root.visit(TransformVisitor())
        root.visit(AtomicOperationsInliner())
        return root.toSource().toByteArray()
    }

    inner class DependencyEraser : NodeVisitor {
        private fun isAtomicfuDependency(node: AstNode) =
            (node.type == Token.STRING && node.toSource() == KOTLINX_ATOMICFU)

        override fun visit(node: AstNode): Boolean {
            when (node.type) {
                Token.ARRAYLIT -> {
                    val elements = (node as ArrayLiteral).elements as MutableList
                    val it = elements.listIterator()
                    while (it.hasNext()) {
                        val arg = it.next()
                        if (isAtomicfuDependency(arg)) {
                            it.remove()
                        }
                    }
                }
                Token.CALL -> {
                    if (node is FunctionCall && node.target.toSource() == FACTORY) {
                        val it = node.arguments.listIterator()
                        while (it.hasNext()) {
                            val arg = it.next()
                            when (arg.type) {
                                Token.GETELEM -> {
                                    if (isAtomicfuDependency((arg as ElementGet).element)) {
                                        it.remove()
                                    }
                                }
                                Token.CALL -> {
                                    if ((arg as FunctionCall).target.toSource() == REQUIRE && !requireKotlinxAtomicfu) {
                                        if (isAtomicfuDependency(arg.arguments[0])) {
                                            it.remove()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Token.GETELEM -> {
                    if (isAtomicfuDependency((node as ElementGet).element)) {
                        val enclosingNode = node.parent
                        if (node.parent.type == Token.TYPEOF) {
                            if (enclosingNode.parent.parent.type == Token.IF) {
                                val ifStatement = enclosingNode.parent.parent as IfStatement
                                ifStatement.thenPart = Block()
                            }
                        }
                    }
                }
            }
            return true
        }
    }

    inner class AtomicConstructorDetector : NodeVisitor {
        override fun visit(node: AstNode?): Boolean {
            if (node is VariableInitializer && node.initializer is PropertyGet) {
                val initializer = (node.initializer as PropertyGet).property.toSource()
                if (initializer.matches(Regex(ATOMIC_CONSTRUCTOR))) {
                    atomicConstructors.add(node.target.toSource())
                    node.initializer = null
                }
                if (initializer.matches(Regex(ATOMIC_ARRAY_CONSTRUCTOR)) ) {
                    val initialValue = when (initializer.substringAfterLast('$')) {
                        "int" -> "0"
                        "long" -> "0"
                        "boolean" -> "false"
                        else -> null
                    }
                    atomicArrayConstructors[node.target.toSource()] = initialValue
                    node.initializer = null
                }
                return false
            }
            return true
        }
    }

    inner class TransformVisitor : NodeVisitor {
        override fun visit(node: AstNode): Boolean {
            // remove atomic constructors from classes fields
            if (node is FunctionCall) {
                val functionName = node.target.toSource()
                if (atomicConstructors.contains(functionName)) {
                    if (node.parent is Assignment) {
                        val valueNode = node.arguments[0]
                        (node.parent as InfixExpression).setRight(valueNode)
                    }
                    return true
                } else if (atomicArrayConstructors.contains(functionName)) {
                    val arrayConstructor = Name()
                    arrayConstructor.identifier = ARRAY
                    node.target = arrayConstructor
                    atomicArrayConstructors[functionName]?.let{
                        val arrayConsCall = FunctionCall()
                        arrayConsCall.target = node.target
                        arrayConsCall.arguments = node.arguments
                        val target = PropertyGet()
                        val fill = Name()
                        fill.identifier = FILL
                        target.target = arrayConsCall
                        target.property = fill
                        node.target = target
                        val initialValue = Name()
                        initialValue.identifier = it
                        node.arguments = listOf(initialValue)
                    }
                    return true
                } else {
                    if (node.target is PropertyGet && (node.target as PropertyGet).target is FunctionCall) {
                        val callTarget = node.target as PropertyGet
                        val funcCall = (node.target as PropertyGet).target as FunctionCall
                        val getter = getArrayElement(funcCall)
                        callTarget.target = getter
                    }
                }

            }
            // remove value property call
            if (node.type == Token.GETPROP) {
                if ((node as PropertyGet).property.toSource() == MANGLED_VALUE_PROP) {
                    // A.a.value
                    if (node.target.type == Token.GETPROP) {
                        val clearField = node.target as PropertyGet
                        val targetNode = clearField.target
                        val clearProperety = clearField.property
                        node.setLeftAndRight(targetNode, clearProperety)
                    } else if (node.target.type == Token.CALL) {
                        val funcCall = node.target as FunctionCall
                        val funcName = (funcCall.target as PropertyGet).property.toSource()
                        if (funcName == GET_ELEMENT) {
                            val getter = getArrayElement(funcCall)
                            node.target = getter.target
                            node.property = getter.property
                        } else {
                            val funcTarget = (funcCall.target as PropertyGet).target
                            val call = Name()
                            val args = funcCall.arguments.fold("") { acc, arg -> acc + arg.toSource()}
                            call.identifier = "$funcName($args)"
                            node.target = funcTarget
                            node.property = call
                        }
                    }
                    // other cases with $receiver.kotlinx$atomicfu$value in inline functions
                    else if (node.target.toSource() == RECEIVER) {
                        val rr = ReceiverResolver()
                        node.enclosingFunction.visit(rr)
                        if (rr.receiver != null) {
                            val field = rr.receiver as PropertyGet
                            node.setLeftAndRight(field.target, field.property)
                        }
                    }
                }
            }
            return true
        }

        private fun getArrayElement(getterCall: FunctionCall): PropertyGet {

            val index = getterCall.arguments[0].toSource()
            val f = ((getterCall.target as PropertyGet).target as PropertyGet).target
            val arrName = ((getterCall.target as PropertyGet).target as PropertyGet).property
            val getter = Name()
            getter.identifier = "${arrName.toSource()}[$index]" //intArr[]
            val newGetterCall = PropertyGet()
            newGetterCall.target = f
            newGetterCall.property = getter
            return newGetterCall
        }
    }

    // receiver data flow
    inner class ReceiverResolver : NodeVisitor {
        var receiver: AstNode? = null
        override fun visit(node: AstNode): Boolean {
            if (node is VariableInitializer) {
                if (node.target.toSource() == RECEIVER) {
                    receiver = node.initializer
                    return false
                }
            }
            return true
        }
    }

    inner class AtomicOperationsInliner : NodeVisitor {
        override fun visit(node: AstNode?): Boolean {
            // inline atomic operations
            if (node is FunctionCall) {
                if (node.target is PropertyGet) {
                    val funcName = (node.target as PropertyGet).property
                    var field = (node.target as PropertyGet).target
                    if (field.toSource() == RECEIVER) {
                        val rr = ReceiverResolver()
                        node.enclosingFunction.visit(rr)
                        if (rr.receiver != null) {
                            field = rr.receiver
                        }
                    }
                    val args = node.arguments
                    val inlined = node.inlineAtomicOperation(funcName.toSource(), field, args)
                    return !inlined
                }
            }
            return true
        }
    }

    private fun AstNode.isThisNode(): Boolean {
        return if (this is PropertyGet) {
            if (target.type == Token.THIS) true else target.isThisNode()
        } else {
            (this.type == Token.THIS)
        }
    }

    private fun PropertyGet.resolvePropName(): String {
        val target = this.target
        return if (target is PropertyGet) {
            "${target.resolvePropName()}.${property.toSource()}"
        } else {
            property.toSource()
        }
    }

    private fun AstNode.scopedSource(): String {
        return if (this.isThisNode() && this is PropertyGet) {
            val property = resolvePropName()
            "$SCOPE.$property"
        } else if (this.type == Token.THIS) {
            SCOPE
        } else {
            this.toSource()
        }
    }

    private fun FunctionCall.inlineAtomicOperation(
        funcName: String,
        field: AstNode,
        args: List<AstNode>
    ): Boolean {
        val f = field.scopedSource()
        val code = when (funcName) {
            "getAndSet\$atomicfu" -> {
                val arg = args[0].toSource()
                "(function($SCOPE) {var oldValue = $f; $f = $arg; return oldValue;})"
            }
            "compareAndSet\$atomicfu" -> {
                val expected = args[0].scopedSource()
                val updated = args[1].scopedSource()
                "(function($SCOPE) {return $f === $expected ? function() { $f = $updated; return true }() : false})"
            }
            "getAndIncrement\$atomicfu" -> {
                "(function($SCOPE) {return $f++;})"
            }

            "getAndIncrement\$atomicfu\$long" -> {
                "(function($SCOPE) {var oldValue = $f; $f = $f.inc(); return oldValue;})"
            }

            "getAndDecrement\$atomicfu" -> {
                "(function($SCOPE) {return $f--;})"
            }

            "getAndDecrement\$atomicfu\$long" -> {
                "(function($SCOPE) {var oldValue = $f; $f = $f.dec(); return oldValue;})"
            }

            "getAndAdd\$atomicfu" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {var oldValue = $f; $f += $arg; return oldValue;})"
            }

            "getAndAdd\$atomicfu\$long" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {var oldValue = $f; $f = $f.add($arg); return oldValue;})"
            }

            "addAndGet\$atomicfu" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {$f += $arg; return $f;})"
            }

            "addAndGet\$atomicfu\$long" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {$f = $f.add($arg); return $f;})"
            }

            "incrementAndGet\$atomicfu" -> {
                "(function($SCOPE) {return ++$f;})"
            }

            "incrementAndGet\$atomicfu\$long" -> {
                "(function($SCOPE) {return $f = $f.inc();})"
            }

            "decrementAndGet\$atomicfu" -> {
                "(function($SCOPE) {return --$f;})"
            }

            "decrementAndGet\$atomicfu\$long" -> {
                "(function($SCOPE) {return $f = $f.dec();})"
            }
            else -> null
        }
        if (code != null) {
            this.setImpl(code)
            return true
        }
        return false
    }

    private fun FunctionCall.setImpl(code: String) {
        val p = Parser(CompilerEnvirons())
        val node = p.parse(code, null, 0)
        if (node.firstChild != null) {
            val expr = (node.firstChild as ExpressionStatement).expression
            val func = (expr as ParenthesizedExpression).expression as FunctionNode
            (node.firstChild as ExpressionStatement).expression =
                ParenthesizedExpressionDerived(FunctionNodeDerived(func))
            this.target = (node.firstChild as ExpressionStatement).expression
            val thisNode = Parser(CompilerEnvirons()).parse("this", null, 0)
            this.arguments = listOf((thisNode.firstChild as ExpressionStatement).expression)
        }
    }
}

private class ParenthesizedExpressionDerived(val expr: FunctionNode) : ParenthesizedExpression() {
    override fun toSource(depth: Int): String = "(" + expr.toSource(0) + ")"
}

// local FunctionNode parser for atomic operations to avoid internal formatting
private class FunctionNodeDerived(val fn: FunctionNode) : FunctionNode() {

    override fun toSource(depth: Int) = buildString {
        append("function")
        append("(")
        printList(fn.params, this)
        append(") ")
        append("{")
        (fn.body as? Block)?.forEach {
            when (it.type) {
                Token.RETURN -> {
                    val retVal = (it as ReturnStatement).returnValue
                    when (retVal.type) {
                        Token.HOOK -> {
                            val cond = retVal as ConditionalExpression
                            append("return ")
                            append(cond.testExpression.toSource())
                            append(" ? ")
                            val target = (cond.trueExpression as FunctionCall).target as FunctionNode
                            (cond.trueExpression as FunctionCall).target = FunctionNodeDerived(target)
                            append(cond.trueExpression.toSource())
                            append(" : ")
                            append(cond.falseExpression.toSource())
                        }
                        else -> {
                            append("return").append(" ").append(retVal.toSource()).append(";")
                        }
                    }
                }
                Token.VAR -> {
                    if (it is VariableDeclaration) {
                        append("var").append(" ")
                        printList(it.variables, this)
                        if (it.isStatement) {
                            append(";")
                        }
                    }
                }
                Token.EXPR_VOID -> {
                    if (it is ExpressionStatement) {
                        append(it.expression.toSource()).append(";")
                    }
                }
            }
        }
        append("}")
    }
}

fun main(args: Array<String>) {
    if (args.size !in 1..3) {
        println("Usage: AtomicFUTransformerKt <dir> [<output>]")
        return
    }
    val t = AtomicFUTransformerJS(File(args[0]), File(args[1]))
    if (args.size > 2) {
        t.requireKotlinxAtomicfu = true
    }
    t.transform()
}