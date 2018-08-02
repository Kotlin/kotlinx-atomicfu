package kotlinx.atomicfu.transformer

import org.mozilla.javascript.*
import org.mozilla.javascript.ast.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.lang.String.format

class AtomicFUTransformerJS(
    var inputDir: File,
    var outputDir: File
) {
    private val p = Parser(CompilerEnvirons())
    private val atomicConstructors = mutableSetOf<String>()
    private val atomicArrayConstructors = mutableMapOf<String, String?>()

    private operator fun File.div(child: String) =
        File(this, child)

    private fun File.toOutputFile(): File =
        outputDir / relativeTo(inputDir).toString()

    private var logger = LoggerFactory.getLogger(AtomicFUTransformer::class.java)

    private fun info(message: String, sourceInfo: SourceInfo? = null) {
        logger.info(format(message, sourceInfo))
    }

    private fun File.mkdirsAndWrite(outBytes: ByteArray) {
        parentFile.mkdirs()
        writeBytes(outBytes) // write resulting bytes
    }

    fun transform() {
        //if (outputDir == inputDir) {
            // perform transformation
            info("Transforming to $outputDir")
            inputDir.walk().filter { it.isFile }.forEach { file ->
                val bytes = file.readBytes()
                val outBytes = transformFile(file, bytes)
                val outFile = file.toOutputFile()
                outFile.mkdirsAndWrite(outBytes)
            }
//        } else {
//            info("Nothing to transform -- all classes are up to date")
//        }
    }

    private fun transformFile(file: File, bytes: ByteArray): ByteArray {
        val root = p.parse(FileReader(file), null, 0)
        val tv = TransformVisitor()
        val acf = AtomicConstructorDetector()
        root.visit(acf)
        root.visit(tv)
        return root.toSource().toByteArray()
    }

    inner class AtomicConstructorDetector : NodeVisitor {

        override fun visit(node: AstNode?): Boolean {
            if (node is VariableInitializer && node.initializer is PropertyGet) {
                val regexVal = Regex("""atomic\$(ref|int|long|boolean)\$""")
                val regexArray = Regex("""Atomic(Ref|Int|Long|Boolean)Array\$(ref|int|long|boolean)""")
                val initializer = (node.initializer as PropertyGet).property.toSource()
                if (initializer.matches(regexVal)) {
                    atomicConstructors.add(node.target.toSource())
                }
                if (initializer.matches(regexArray) ) {
                    val initialValue = when (initializer.substringAfterLast('$')) {
                        "int" -> "0"
                        "long" -> "0"
                        "boolean" -> "false"
                        else -> null
                    }
                    atomicArrayConstructors[node.target.toSource()] = initialValue
                }
                return false
            }
            return true
        }
    }

    inner class TransformVisitor : NodeVisitor {

        override fun visit(node: AstNode): Boolean {
            // inline atomic operations
            if (node is FunctionCall) {
                if (node.target is PropertyGet) {
                    val funcName = (node.target as PropertyGet).property
                    var field = (node.target as PropertyGet).target
                    // invoking function on array element
                    if (field is FunctionCall && field.target is PropertyGet) {
                        val target = field.target as PropertyGet
                        if (target.property.toSource() == "get\$atomicfu") {
                            field = getArrayElement(field)
                        }
                    }
                    if (field.toSource() == "\$receiver") {
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

            //remove atomic constructors from classes fields
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
                    arrayConstructor.identifier = "Array"
                    node.target = arrayConstructor
                    atomicArrayConstructors[functionName]?.let{
                        val arrayConsCall = FunctionCall()
                        arrayConsCall.target = node.target
                        arrayConsCall.arguments = node.arguments
                        val target = PropertyGet()
                        val fill = Name()
                        fill.identifier = "fill"
                        target.target = arrayConsCall
                        target.property = fill
                        node.target = target
                        val initialValue = Name()
                        initialValue.identifier = it
                        node.arguments = listOf(initialValue)
                    }
                    return true
                }

            }

            // remove value property call
            if (node.type == Token.GETPROP) {
                if ((node as PropertyGet).property.toSource() == "kotlinx\$atomicfu\$value") {
                    // A.a.value
                    if (node.target.type == Token.GETPROP) {
                        val clearField = node.target as PropertyGet
                        val targetNode = clearField.target
                        val clearProperety = clearField.property
                        node.setLeftAndRight(targetNode, clearProperety)
                    } else if (node.target.type == Token.CALL) {
                        val funcCall = node.target as FunctionCall
                        val funcName = (funcCall.target as PropertyGet).property.toSource()
                        if (funcName == "get\$atomicfu") {
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
                    else if (node.target.toSource() == "\$receiver") {
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
        override fun visit(node: AstNode?): Boolean {
            if (node is VariableInitializer) {
                if (node.target.toSource() == "\$receiver") {
                    receiver = node.initializer
                    return false
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
        var code: String? = null
        val f = if (passScope) ("scope" + '.' + (field as PropertyGet).property.toSource()) else field.toSource()
        when (funcName) {
            "getAndSet\$atomicfu" -> {
                val arg = args[0].toSource()
                code = "(function(scope) {var oldValue = $f; $f = $arg; return oldValue;})"
            }
            "compareAndSet\$atomicfu" -> {
                val expected = args[0].toSource()
                val updated = args[1].toSource()
                code =
                    "(function(scope) {return $f === $expected ? function() { $f = $updated; return true }() : false})"
            }
            "getAndIncrement\$atomicfu" -> {
                code = "(function(scope) {return $f++;})"
            }

            "getAndIncrement\$atomicfu\$long" -> {
                code = "(function(scope) {var oldValue = $f; $f = $f.inc(); return oldValue;})"
            }

            "getAndDecrement\$atomicfu" -> {
                code = "(function(scope) {return $f--;})"
            }

            "getAndDecrement\$atomicfu\$long" -> {
                code = "(function(scope) {var oldValue = $f; $f = $f.dec(); return oldValue;})"
            }

            "getAndAdd\$atomicfu" -> {
                val arg = args[0].toSource()
                code = "(function(scope) {var oldValue = $f; $f += $arg; return oldValue;})"
            }

            "getAndAdd\$atomicfu\$long" -> {
                val arg = args[0].toSource()
                code = "(function(scope) {var oldValue = $f; $f = $f.add($arg); return oldValue;})"
            }

            "addAndGet\$atomicfu" -> {
                val arg = args[0].toSource()
                code = "(function(scope) {$f += $arg; return $f;})"
            }

            "addAndGet\$atomicfu\$long" -> {
                val arg = args[0].toSource()
                code = "(function(scope) {$f = $f.add($arg); return $f;})"
            }

            "incrementAndGet\$atomicfu" -> {
                code = "(function(scope) {return ++$f;})"
            }

            "incrementAndGet\$atomicfu\$long" -> {
                code = "(function(scope) {return $f = $f.inc();})"
            }

            "decrementAndGet\$atomicfu" -> {
                code = "(function(scope) {return --$f;})"
            }

            "decrementAndGet\$atomicfu\$long" -> {
                code = "(function(scope) {return $f = $f.dec();})"
            }
        }
        if (code != null) {
            this.getNode(code)
            return true
        }
        return false
    }

    private fun FunctionCall.getNode(code: String) {
        val p = Parser(CompilerEnvirons())
        val node = p.parse(code, null, 0)
        if (node.firstChild != null) {
            val expr = (node.firstChild as ExpressionStatement).expression
            val func = (expr as ParenthesizedExpression).expression as FunctionNode
            (node.firstChild as ExpressionStatement).expression = ParenthesizedExpressionDerived(FunctionNodeDerived(func))
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

    override fun toSource(depth: Int): String {
        val sb = StringBuilder()
        sb.append("function")
        sb.append("(")
        printList(fn.params, sb)
        sb.append(") ")

        sb.append("{")
        (fn.body as Block).forEach {
            val sbStmt = StringBuilder()
            when (it.type) {
                Token.RETURN -> {
                    val retVal = (it as ReturnStatement).returnValue
                    when (retVal.type) {
                        Token.HOOK -> {
                            val cond = retVal as ConditionalExpression
                            sbStmt.append("return ")
                            sbStmt.append(cond.testExpression.toSource())
                            sbStmt.append(" ? ")
                            val target = (cond.trueExpression as FunctionCall).target as FunctionNode
                            (cond.trueExpression as FunctionCall).target = FunctionNodeDerived(target)
                            sbStmt.append(cond.trueExpression.toSource())
                            sbStmt.append(" : ")
                            sbStmt.append(cond.falseExpression.toSource())
                        }
                        else -> {
                            sbStmt.append("return").append(" ").append(retVal.toSource()).append(";")
                        }
                    }
                }
                Token.VAR -> {
                    if (it is VariableDeclaration) {
                        sbStmt.append("var").append(" ")
                        printList(it.variables, sbStmt)
                        if (it.isStatement) {
                            sbStmt.append(";")
                        }
                    }
                }
                Token.EXPR_VOID -> {
                    if (it is ExpressionStatement) {
                        sbStmt.append(it.expression.toSource()).append(";")
                    }
                }
            }
            sb.append(sbStmt.toString())
        }
        sb.append("}")
        return sb.toString()
    }
}


fun main(args: Array<String>) {
    if (args.size !in 1..2) {
        println("Usage: AtomicFUTransformerKt <dir> [<output>]")
        return
    }
    val output = File(args[1])
    output.createNewFile()
    val t = AtomicFUTransformerJS(File(args[0]), output)
    t.transform()
}