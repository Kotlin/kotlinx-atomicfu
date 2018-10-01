package kotlinx.atomicfu.transformer

import org.mozilla.javascript.*
import org.mozilla.javascript.ast.*
import java.io.File
import java.io.FileReader
import org.mozilla.javascript.Token

private const val ATOMIC_CONSTRUCTOR = """atomic\$(ref|int|long|boolean)\$"""
private const val MANGLED_VALUE_PROP = "kotlinx\$atomicfu\$value"
private const val RECEIVER = "\$receiver"
private const val SCOPE = "scope"
private const val FACTORY = "factory"
private const val REQUIRE = "require"
private const val KOTLINX_ATOMICFU = "'kotlinx-atomicfu'"

class AtomicFUTransformerJS(
    inputDir: File,
    outputDir: File,
    var requireKotlinxAtomicfu: Boolean = false
) : AtomicFUTransformerBase(inputDir, outputDir) {
    private val atomicConstructors = mutableSetOf<String>()

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
                if ((node.initializer as PropertyGet).property.toSource().matches(Regex(ATOMIC_CONSTRUCTOR))) {
                    atomicConstructors.add(node.target.toSource())
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
                    var passScope = false
                    if (field is PropertyGet && field.target.type == Token.THIS) {
                        passScope = true
                    }
                    val args = node.arguments
                    val inlined = node.inlineAtomicOperation(funcName.toSource(), field, args, passScope)
                    return !inlined
                }
            }
            return true
        }
    }

    private fun FunctionCall.inlineAtomicOperation(
        funcName: String,
        field: AstNode,
        args: List<AstNode>,
        passScope: Boolean
    ): Boolean {
        val f = if (passScope) (SCOPE + '.' + (field as PropertyGet).property.toSource()) else field.toSource()
        val code = when (funcName) {
            "getAndSet\$atomicfu" -> {
                val arg = args[0].toSource()
                "(function($SCOPE) {var oldValue = $f; $f = $arg; return oldValue;})"
            }
            "compareAndSet\$atomicfu" -> {
                val expected = args[0].scopedSource()
                val updated = args[1].toSource()
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
                val arg = args[0].toSource()
                "(function($SCOPE) {var oldValue = $f; $f += $arg; return oldValue;})"
            }

            "getAndAdd\$atomicfu\$long" -> {
                val arg = args[0].toSource()
                "(function($SCOPE) {var oldValue = $f; $f = $f.add($arg); return oldValue;})"
            }

            "addAndGet\$atomicfu" -> {
                val arg = args[0].toSource()
                "(function($SCOPE) {$f += $arg; return $f;})"
            }

            "addAndGet\$atomicfu\$long" -> {
                val arg = args[0].toSource()
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
    
    private fun AstNode.scopedSource(): String {
        if (this.type == Token.THIS) {
            return this.toSource().replaceFirst("this", SCOPE)
        }
        return this.toSource()
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
