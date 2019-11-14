/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.transformer

import org.mozilla.javascript.*
import org.mozilla.javascript.ast.*
import java.io.File
import java.io.FileReader
import org.mozilla.javascript.Token
import java.util.regex.*

private const val ATOMIC_CONSTRUCTOR = """(atomic\$(ref|int|long|boolean)\$|Atomic(Ref|Int|Long|Boolean))"""
private const val ATOMIC_ARRAY_CONSTRUCTOR = """Atomic(Ref|Int|Long|Boolean)Array\$(ref|int|long|boolean|ofNulls)"""
private const val MANGLED_VALUE_PROP = "kotlinx\$atomicfu\$value"

private const val RECEIVER = "\$receiver"
private const val SCOPE = "scope"
private const val FACTORY = "factory"
private const val REQUIRE = "require"
private const val KOTLINX_ATOMICFU = "'kotlinx-atomicfu'"
private const val KOTLINX_ATOMICFU_PACKAGE = "kotlinx.atomicfu"
private const val KOTLIN_TYPE_CHECK = "Kotlin.isType"
private const val ATOMIC_REF = "AtomicRef"
private const val MODULE_KOTLINX_ATOMICFU = "\\\$module\\\$kotlinx_atomicfu"
private const val ARRAY = "Array"
private const val FILL = "fill"
private const val GET_ELEMENT = "get\\\$atomicfu"
private const val LOCKS = "locks"
private const val REENTRANT_LOCK_ATOMICFU_SINGLETON = "$LOCKS.reentrantLock\\\$atomicfu"

private val MANGLE_VALUE_REGEX = Regex(".${Pattern.quote(MANGLED_VALUE_PROP)}")
// matches index until the first occurence of ')', parenthesised index expressions not supported
private val ARRAY_GET_ELEMENT_REGEX = Regex(".$GET_ELEMENT\\((.*)\\)")

class AtomicFUTransformerJS(
    inputDir: File,
    outputDir: File
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
        return root.eraseGetValue().toByteArray()
    }

    // erase getting value of atomic field
    private fun AstNode.eraseGetValue(): String {
        var res = this.toSource()
        val primitiveGetValue = MANGLE_VALUE_REGEX
        val arrayGetElement = ARRAY_GET_ELEMENT_REGEX
        while (res.contains(arrayGetElement)) {
            res = res.replace(arrayGetElement) { matchResult ->
                val greedyToLastClosingParen = matchResult.groupValues[1]
                var balance = 1
                var indexEndPos = 0
                for (i in 0 until greedyToLastClosingParen.length) {
                    val c = greedyToLastClosingParen[i]
                    if (c == '(') balance++
                    if (c == ')') balance--
                    if (balance == 0) {
                        indexEndPos = i
                        break
                    }
                }
                val closingParen = indexEndPos == greedyToLastClosingParen.lastIndex
                if (balance == 1) {
                    "[$greedyToLastClosingParen]"
                } else {
                    "[${greedyToLastClosingParen.substring(0, indexEndPos)}]${greedyToLastClosingParen.substring(indexEndPos + 1)}${if (!closingParen) ")" else ""}"
                }
            }
        }
        return res.replace(primitiveGetValue) { "" }
    }

    inner class DependencyEraser : NodeVisitor {
        private fun isAtomicfuDependency(node: AstNode) =
            (node.type == Token.STRING && node.toSource() == KOTLINX_ATOMICFU)

        private fun isAtomicfuModule(node: AstNode) =
            (node.type == Token.NAME && node.toSource().matches(Regex(MODULE_KOTLINX_ATOMICFU)))

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
                            if (isAtomicfuModule(it.next())) {
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
        private fun kotlinxAtomicfuModuleName(name: String) = "$MODULE_KOTLINX_ATOMICFU.$KOTLINX_ATOMICFU_PACKAGE.$name"

        override fun visit(node: AstNode?): Boolean {
            if (node is Block) {
                for (stmt in node) {
                    if (stmt is VariableDeclaration) {
                        val varInit = stmt.variables[0] as VariableInitializer
                        if (varInit.initializer is PropertyGet) {
                            val initializer = varInit.initializer.toSource()
                            if (initializer.matches(Regex(kotlinxAtomicfuModuleName(ATOMIC_CONSTRUCTOR)))) {
                                atomicConstructors.add(varInit.target.toSource())
                                node.replaceChild(stmt, EmptyLine())
                            } else if (initializer.matches(Regex(kotlinxAtomicfuModuleName(LOCKS)))){
                                node.replaceChild(stmt, EmptyLine())
                            }
                        }
                    }
                }
            }
            if (node is VariableInitializer && node.initializer is PropertyGet) {
                val initializer = node.initializer.toSource()
                if (initializer.matches(Regex(REENTRANT_LOCK_ATOMICFU_SINGLETON))) {
                    node.initializer = null
                }
                if (initializer.matches(Regex(kotlinxAtomicfuModuleName(ATOMIC_CONSTRUCTOR)))) {
                    atomicConstructors.add(node.target.toSource())
                    node.initializer = null
                }
                if (initializer.matches(Regex(kotlinxAtomicfuModuleName(ATOMIC_ARRAY_CONSTRUCTOR)))) {
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
            } else if (node is Assignment && node.right is PropertyGet) {
                val initializer = node.right.toSource()
                if (initializer.matches(Regex(REENTRANT_LOCK_ATOMICFU_SINGLETON))) {
                    node.right = Name().also { it.identifier = "null" }
                    return false
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
                } else if (atomicArrayConstructors.contains(functionName)) {
                    val arrayConstructor = Name()
                    arrayConstructor.identifier = ARRAY
                    node.target = arrayConstructor
                    atomicArrayConstructors[functionName]?.let {
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
                } else if (node.target is PropertyGet) {
                    if ((node.target as PropertyGet).target is FunctionCall) {
                        val atomicOperationTarget = node.target as PropertyGet
                        val funcCall = atomicOperationTarget.target as FunctionCall
                        if (funcCall.target is PropertyGet) {
                            val getterCall = (funcCall.target as PropertyGet).property
                            if (Regex(GET_ELEMENT).matches(getterCall.toSource())) {
                                val getter = getArrayElement(funcCall)
                                atomicOperationTarget.target = getter
                            }
                        }
                    }
                }
            }
            // remove value property call
            if (node is PropertyGet) {
                if (node.property.toSource() == MANGLED_VALUE_PROP) {
                    // check whether atomic operation is performed on the type casted atomic field
                    node.target.eraseAtomicFieldFromUncheckedCast()?.let { node.target = it }
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
                        rr.receiver?.let { node.target = it }
                    }
                }
            }
            return true
        }

        private fun getArrayElement(getterCall: FunctionCall): AstNode {
            val index = getterCall.arguments[0]
            val arrayField = (getterCall.target as PropertyGet).target
            // whether this field is static or not
            val isStatic = arrayField !is PropertyGet
            val arrName = if (isStatic) arrayField else (arrayField as PropertyGet).property
            val getter = ElementGet(arrName, index)
            return if (isStatic) { //intArr[index]
                getter
            } else { //A.intArr[0]
                val call = PropertyGet()
                call.target = (arrayField as PropertyGet).target
                val name = Name()
                name.identifier = getter.toSource()
                call.property = name
                call
            }
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
                    field.eraseAtomicFieldFromUncheckedCast()?.let { field = it }
                    val args = node.arguments
                    val inlined = node.inlineAtomicOperation(funcName.toSource(), field, args)
                    return !inlined
                }
            }
            return true
        }
    }

    private fun AstNode.eraseAtomicFieldFromUncheckedCast(): AstNode? {
        if (this is ParenthesizedExpression && expression is ConditionalExpression) {
            val testExpression = (expression as ConditionalExpression).testExpression
            if (testExpression is FunctionCall && testExpression.target.toSource() == KOTLIN_TYPE_CHECK) {
                // type check
                val typeToCast = testExpression.arguments[1]
                if ((typeToCast as Name).identifier == ATOMIC_REF) {
                    // unchecked type cast -> erase atomic field itself
                    return (testExpression.arguments[0] as Assignment).right
                }
            }
        }
        return null
    }

    private fun AstNode.isThisNode(): Boolean {
        return when(this) {
            is PropertyGet -> {
                target.isThisNode()
            }
            is FunctionCall -> {
                target.isThisNode()
            }
            else -> {
                (this.type == Token.THIS)
            }
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
        if (this.isThisNode()) {
            if (this is PropertyGet) {
                val property = resolvePropName()
                return "$SCOPE.$property"
            } else if (this is FunctionCall && this.target is PropertyGet) {
                // check that this function call is getting array element
                if (this.target is PropertyGet) {
                    val funcName = (this.target as PropertyGet).property.toSource()
                    if (Regex(GET_ELEMENT).matches(funcName)) {
                        val property = (this.target as PropertyGet).resolvePropName()
                        return "$SCOPE.$property(${this.arguments[0].toSource()})"
                    }
                }
            } else if (this.type == Token.THIS) {
                return SCOPE
            }
        }
        return this.toSource()
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
                "(function($SCOPE) {var oldValue = $f; $f = $arg; return oldValue;})()"
            }
            "compareAndSet\$atomicfu" -> {
                val expected = args[0].scopedSource()
                val updated = args[1].scopedSource()
                val equals = if (expected == "null") "==" else "==="
                "(function($SCOPE) {return $f $equals $expected ? function() { $f = $updated; return true }() : false})()"
            }
            "getAndIncrement\$atomicfu" -> {
                "(function($SCOPE) {return $f++;})()"
            }

            "getAndIncrement\$atomicfu\$long" -> {
                "(function($SCOPE) {var oldValue = $f; $f = $f.inc(); return oldValue;})()"
            }

            "getAndDecrement\$atomicfu" -> {
                "(function($SCOPE) {return $f--;})()"
            }

            "getAndDecrement\$atomicfu\$long" -> {
                "(function($SCOPE) {var oldValue = $f; $f = $f.dec(); return oldValue;})()"
            }

            "getAndAdd\$atomicfu" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {var oldValue = $f; $f += $arg; return oldValue;})()"
            }

            "getAndAdd\$atomicfu\$long" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {var oldValue = $f; $f = $f.add($arg); return oldValue;})()"
            }

            "addAndGet\$atomicfu" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {$f += $arg; return $f;})()"
            }

            "addAndGet\$atomicfu\$long" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {$f = $f.add($arg); return $f;})()"
            }

            "incrementAndGet\$atomicfu" -> {
                "(function($SCOPE) {return ++$f;})()"
            }

            "incrementAndGet\$atomicfu\$long" -> {
                "(function($SCOPE) {return $f = $f.inc();})()"
            }

            "decrementAndGet\$atomicfu" -> {
                "(function($SCOPE) {return --$f;})()"
            }

            "decrementAndGet\$atomicfu\$long" -> {
                "(function($SCOPE) {return $f = $f.dec();})()"
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
            this.target = (expr as FunctionCall).target
            val thisNode = Parser(CompilerEnvirons()).parse("this", null, 0)
            this.arguments = listOf((thisNode.firstChild as ExpressionStatement).expression)
        }
    }
}

private class EmptyLine: EmptyExpression() {
    override fun toSource(depth: Int) = "\n"
}

fun main(args: Array<String>) {
    if (args.size !in 1..2) {
        println("Usage: AtomicFUTransformerKt <dir> [<output>]")
        return
    }
    val t = AtomicFUTransformerJS(File(args[0]), File(args[1]))
    t.transform()
}