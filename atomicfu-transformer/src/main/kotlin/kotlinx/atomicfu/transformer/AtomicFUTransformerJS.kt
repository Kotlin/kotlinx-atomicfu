package kotlinx.atomicfu.transformer

import org.mozilla.javascript.*
import org.mozilla.javascript.ast.*
import java.io.File
import java.io.FileReader
import org.mozilla.javascript.Token

private const val ATOMIC_CONSTRUCTOR = """atomic\$(ref|int|long|boolean)\$"""
private const val TRACE_CONSTRUCTOR = "atomicfu\\\$trace\\\$"
private const val TRACE_APPEND = "atomicfu\\\$trace\\\$append\\\$"
private const val MANGLED_VALUE_PROP = "kotlinx\$atomicfu\$value"
private const val RECEIVER = "\$receiver"
private const val SCOPE = "scope"
private const val FACTORY = "factory"
private const val REQUIRE = "require"
private const val KOTLINX_ATOMICFU = "'kotlinx-atomicfu'"
private const val MODULE_KOTLINX_ATOMICFU = "\$module\$kotlinx_atomicfu"

class AtomicFUTransformerJS(
    inputDir: File,
    outputDir: File,
    var requireKotlinxAtomicfu: Boolean = false
) : AtomicFUTransformerBase(inputDir, outputDir) {
    private val atomicConstructors = mutableSetOf<String>()
    private val traceConstructors = mutableSetOf<String>()

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
        root.visit(TraceErasor())
        root.visit(TransformVisitor())
        root.visit(AtomicOperationsInliner())
        return root.toSource().toByteArray()
    }

    inner class DependencyEraser : NodeVisitor {
        private fun isAtomicfuDependency(node: AstNode) =
            (node.type == Token.STRING && node.toSource() == KOTLINX_ATOMICFU)

        private fun isAtomicfuModule(node: AstNode) =
            (node.type == Token.NAME && node.toSource() == MODULE_KOTLINX_ATOMICFU)

        override fun visit(node: AstNode): Boolean {
            when (node.type) {
                Token.ARRAYLIT -> {
                    // erasing 'kotlinx-atomicfu' from the list of defined dependencies
                    val elements = (node as ArrayLiteral).elements as MutableList
                    val it = elements.listIterator()
                    while (it.hasNext()) {
                        val arg = it.next()
                        if (isAtomicfuDependency(arg)) {
                            it.remove()
                        }
                    }
                }
                Token.FUNCTION -> {
                    // erasing 'kotlinx-atomicfu' module passed as parameter
                    if (node is FunctionNode) {
                        val it = node.params.listIterator()
                        while (it.hasNext()) {
                            if (isAtomicfuModule(it.next()) && !requireKotlinxAtomicfu) {
                                it.remove()
                            }
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
                                    // erasing 'kotlinx-atomicfu' dependency as factory argument
                                    if (isAtomicfuDependency((arg as ElementGet).element)) {
                                        it.remove()
                                    }
                                }
                                Token.CALL -> {
                                    // erasing require of 'kotlinx-atomicfu' dependency
                                    if ((arg as FunctionCall).target.toSource() == REQUIRE) {
                                        if (isAtomicfuDependency(arg.arguments[0]) && !requireKotlinxAtomicfu) {
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
                        // erasing the check whether 'kotlinx-atomicfu' is defined
                        if (enclosingNode.type == Token.TYPEOF) {
                            if (enclosingNode.parent.parent.type == Token.IF) {
                                val ifStatement = enclosingNode.parent.parent as IfStatement
                                val falseKeyword = KeywordLiteral()
                                falseKeyword.type = Token.FALSE
                                ifStatement.condition = falseKeyword
                                val oneLineBlock = Block()
                                oneLineBlock.addStatement(EmptyLine())
                                ifStatement.thenPart = oneLineBlock
                            }
                        }

                    }
                }
                Token.BLOCK -> {
                    // erasing importsForInline for 'kotlinx-atomicfu'
                    for (stmt in node) {
                        if (stmt is ExpressionStatement) {
                            val expr = stmt.expression
                            if (expr is Assignment && expr.left is ElementGet) {
                                if (isAtomicfuDependency((expr.left as ElementGet).element)) {
                                    node.replaceChild(stmt, EmptyLine())
                                }
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
            if (node is Block) {
                for (stmt in node) {
                    if (stmt is VariableDeclaration) {
                        val varInit = stmt.variables[0] as VariableInitializer
                        if (varInit.initializer is PropertyGet) {
                            val consName = (varInit.initializer as PropertyGet).property.toSource()
                            if (consName.matches(Regex(ATOMIC_CONSTRUCTOR))) {
                                atomicConstructors.add(varInit.target.toSource())
                                node.replaceChild(stmt, EmptyLine())
                            } else if (consName.matches(Regex(TRACE_CONSTRUCTOR))) {
                                traceConstructors.add(varInit.target.toSource())
                                node.replaceChild(stmt, EmptyLine())
                            }
                        }
                    }
                }
            }
            return true
        }
    }

    inner class TraceErasor : NodeVisitor {
        override fun visit(node: AstNode?): Boolean {
            if (node is Block) {
                for (stmt in node) {
                    if (stmt is ExpressionStatement) {
                        if (stmt.expression is Assignment) {
                            // erase field initialisation
                            val assignment = stmt.expression as Assignment
                            if (assignment.right is FunctionCall) {
                                val functionName = (assignment.right as FunctionCall).target.toSource()
                                if (traceConstructors.contains(functionName)) {
                                    node.replaceChild(stmt, EmptyLine())
                                }
                            }
                        }
                        if (stmt.expression is FunctionCall) {
                            // erase append(text) call
                            val funcNode = (stmt.expression as FunctionCall).target
                            if (funcNode is PropertyGet && funcNode.property.toSource().matches(Regex(TRACE_APPEND))) {
                                node.replaceChild(stmt, EmptyLine())
                            }
                        }
                    }
                }
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
                        (node.parent as InfixExpression).right = valueNode
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

private class EmptyLine: EmptyExpression() {
    override fun toSource(depth: Int) = "\n"
}

fun main(args: Array<String>) {
    if (args.size !in 1..3) {
        println("Usage: AtomicFUTransformerKt <dir> [<output>]")
        return
    }
    val t = AtomicFUTransformerJS(File(args[0]), File(args[1]))
    if (args.size > 2) {
        t.requireKotlinxAtomicfu = args[2].toBoolean()
    }
    t.transform()
}