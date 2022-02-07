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
private const val ATOMIC_CONSTRUCTOR_BINARY_COMPATIBILITY = """(atomic\$(ref|int|long|boolean)\$1)""" // mangled names for declarations left for binary compatibility
private const val ATOMIC_ARRAY_CONSTRUCTOR = """(atomicfu)\$(Atomic(Ref|Int|Long|Boolean)Array)\$(ref|int|long|boolean|ofNulls)"""
private const val MANGLED_VALUE_PROP = "kotlinx\$atomicfu\$value"

private const val TRACE_CONSTRUCTOR = "atomicfu\\\$Trace"
private const val TRACE_BASE_CLASS = "atomicfu\\\$TraceBase"
private const val TRACE_APPEND = """(atomicfu)\$(Trace)\$(append)\$([1234])""" // [1234] is the number of arguments in the append overload
private const val TRACE_NAMED = "atomicfu\\\$Trace\\\$named"
private const val TRACE_FORMAT = "TraceFormat"
private const val TRACE_FORMAT_CONSTRUCTOR = "atomicfu\\\$$TRACE_FORMAT"
private const val TRACE_FORMAT_FORMAT = "atomicfu\\\$$TRACE_FORMAT\\\$format"

private const val RECEIVER = """(\$(receiver)(_\d+)?)"""
private const val SCOPE = "scope"
private const val FACTORY = "factory"
private const val REQUIRE = "require"
private const val PROTOTYPE = "prototype"
private const val KOTLINX_ATOMICFU = "'kotlinx-atomicfu'"
private const val KOTLINX_ATOMICFU_PACKAGE = "kotlinx.atomicfu"
private const val KOTLIN_TYPE_CHECK = "Kotlin.isType"
private const val ATOMIC_REF = "AtomicRef"
private const val MODULE_KOTLINX_ATOMICFU = "\\\$module\\\$kotlinx_atomicfu"
private const val ARRAY = "Array"
private const val FILL = "fill"
private const val GET_ELEMENT = "atomicfu\\\$get"
private const val ARRAY_SIZE = "atomicfu\$size"
private const val LENGTH = "length"
private const val LOCKS = "locks"
private const val REENTRANT_LOCK_ATOMICFU_SINGLETON = "$LOCKS.atomicfu\\\$reentrantLock"


private val MANGLE_VALUE_REGEX = Regex(".${Pattern.quote(MANGLED_VALUE_PROP)}")
// matches index until the first occurence of ')', parenthesised index expressions not supported
private val ARRAY_GET_ELEMENT_REGEX = Regex(".$GET_ELEMENT\\((.*)\\)")

class AtomicFUTransformerJS(
    inputDir: File,
    outputDir: File
) : AtomicFUTransformerBase(inputDir, outputDir) {
    private val atomicConstructors = mutableSetOf<String>()
    private val delegateToOriginalAtomicField = mutableMapOf<String, Name>()
    private val topLevelDelegatedFieldAccessorToOriginalField = mutableMapOf<String, Name>()
    private val atomicArrayConstructors = mutableMapOf<String, String?>()
    private val traceConstructors = mutableSetOf<String>()
    private val traceFormatObjects = mutableSetOf<String>()

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
        root.visit(FieldDelegatesVisitor())
        root.visit(DelegatedPropertyAccessorsVisitor())
        root.visit(TopLevelDelegatedFieldsAccessorVisitor())
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
                    if (node is FunctionNode) {
                        val it = node.params.listIterator()
                        while (it.hasNext()) {
                            // erasing 'kotlinx-atomicfu' module passed as parameter
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
                            if (initializer.matches(Regex(kotlinxAtomicfuModuleName("""($ATOMIC_CONSTRUCTOR|$ATOMIC_CONSTRUCTOR_BINARY_COMPATIBILITY)""")))) {
                                atomicConstructors.add(varInit.target.toSource())
                                node.replaceChild(stmt, EmptyLine())
                            } else if (initializer.matches(Regex(kotlinxAtomicfuModuleName(TRACE_CONSTRUCTOR)))) {
                                traceConstructors.add(varInit.target.toSource())
                                node.replaceChild(stmt, EmptyLine())
                            } else if (initializer.matches(Regex(kotlinxAtomicfuModuleName("""($LOCKS|$TRACE_FORMAT_CONSTRUCTOR|$TRACE_BASE_CLASS|$TRACE_NAMED)""")))) {
                                node.replaceChild(stmt, EmptyLine())
                            }
                        }
                    }
                }
            }
            if (node is PropertyGet && node.property.toSource().matches(Regex(TRACE_FORMAT_FORMAT))) {
                val target = node.target
                node.property = Name().also { it.identifier = "emptyProperty" }
                if (target is PropertyGet && target.property.toSource().matches(Regex(PROTOTYPE))) {
                    traceFormatObjects.add(target.target.toSource())
                }
            }
            if (node is VariableInitializer && node.initializer is PropertyGet) {
                val initializer = node.initializer.toSource()
                if (initializer.matches(Regex(REENTRANT_LOCK_ATOMICFU_SINGLETON))) {
                    node.initializer = null
                }
                if (initializer.matches(Regex(kotlinxAtomicfuModuleName("""($ATOMIC_CONSTRUCTOR|$ATOMIC_CONSTRUCTOR_BINARY_COMPATIBILITY)""")))) {
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

    inner class FieldDelegatesVisitor : NodeVisitor {
        override fun visit(node: AstNode?): Boolean {
            if (node is FunctionCall) {
                val functionName = node.target.toSource()
                if (atomicConstructors.contains(functionName)) {
                    if (node.parent is Assignment) {
                        val assignment = node.parent as Assignment
                        val atomicField = assignment.left
                        val constructorBlock = ((node.parent.parent as? ExpressionStatement)?.parent as? Block)
                                ?: abort("Incorrect tree structure of the constructor block initializing ${node.parent.toSource()}")
                        // check if there is a delegate field initialized by the reference to this atomic
                        for (stmt in constructorBlock) {
                            if (stmt is ExpressionStatement) {
                                if (stmt.expression is Assignment) {
                                    val delegateAssignment = stmt.expression as Assignment
                                    val initializer = delegateAssignment.right
                                    if (initializer.toSource() == atomicField.toSource()) {
                                        if (delegateAssignment.right is PropertyGet) { // initialization of a class field
                                            // delegate${owner_class} to original atomic field
                                            val delegateFieldName = (delegateAssignment.left as PropertyGet).property.toSource()
                                            val ownerClassName = constructorBlock.enclosingFunction.functionName.identifier
                                            delegateToOriginalAtomicField["$delegateFieldName\$$ownerClassName"] =
                                                    (atomicField as PropertyGet).property
                                        } else { // top-level delegated fields
                                            val delegateFieldName = delegateAssignment.left.toSource()
                                            delegateToOriginalAtomicField[delegateFieldName] = atomicField as Name
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return true
        }
    }

    inner class DelegatedPropertyAccessorsVisitor : NodeVisitor {
        override fun visit(node: AstNode?): Boolean {
            // find ObjectLiteral with accessors of the delegated field (get: FunctionNode, set: FunctionNode)
            // redirect getter/setter from generated delegate field to the original atomic field
            if (node is ObjectLiteral && node.parent is FunctionCall &&
                    ((node.elements.size == 2 && node.elements[1].left.toSource() == "get") ||
                            (node.elements.size == 3 && node.elements[1].left.toSource() == "get" && node.elements[2].left.toSource() == "set"))) {
                // check that these are accessors of the atomic delegate field (check only getter)
                if (node.elements[1].right is FunctionNode) {
                    val getter = node.elements[1].right as FunctionNode
                    if (getter.body.hasChildren() && getter.body.firstChild is ReturnStatement) {
                        val returnStmt = getter.body.firstChild as ReturnStatement
                        if (returnStmt.returnValue is PropertyGet && (returnStmt.returnValue as PropertyGet).property.toSource() == MANGLED_VALUE_PROP) {
                            val delegateField = ((returnStmt.returnValue as PropertyGet).target as PropertyGet).property.toSource()
                            val ownerClassName = ((node.parent as FunctionCall).arguments[0] as PropertyGet).target.toSource()
                            val key = "$delegateField\$$ownerClassName"
                            delegateToOriginalAtomicField[key]?.let { atomicField ->
                                // get() = a$delegate.value -> _a.value
                                getter.replaceAccessedField(true, atomicField)
                                if (node.elements.size == 3) {
                                    // set(v: T) { a$delegate.value = v } -> { _a.value = v }
                                    val setter = node.elements[2].right as FunctionNode
                                    setter.replaceAccessedField(false, atomicField)
                                }
                            }
                        }
                    }
                }
            }
            if (node is ObjectLiteral && node.parent is FunctionCall && ((node.elements.size == 1 && node.elements[0].left.toSource() == "get") ||
                            node.elements.size == 2 && node.elements[0].left.toSource() == "get" && node.elements[1].left.toSource() == "set")) {
                val parent = node.parent as FunctionCall
                if (parent.arguments.size == 3 && parent.arguments[1] is StringLiteral) {
                    val topLevelDelegatedFieldName = (parent.arguments[1] as StringLiteral).value
                    if (topLevelDelegatedFieldName in delegateToOriginalAtomicField) {
                        val originalAtomicFieldName = delegateToOriginalAtomicField[topLevelDelegatedFieldName]!!
                        val getterName = node.elements[0].right.toSource()
                        topLevelDelegatedFieldAccessorToOriginalField[getterName] = originalAtomicFieldName
                        if (node.elements.size == 2) {
                            val setterName = node.elements[1].right.toSource()
                            topLevelDelegatedFieldAccessorToOriginalField[setterName] = originalAtomicFieldName
                        }
                    }
                }
            }
            return true
        }
    }

    private fun FunctionNode.replaceAccessedField(isGetter: Boolean, newField: Name) {
        val propertyGet = if (isGetter) {
            (body.firstChild as ReturnStatement).returnValue as PropertyGet
        } else {
            ((body.firstChild as ExpressionStatement).expression as Assignment).left as PropertyGet
        }
        if (propertyGet.target is PropertyGet) { // class member
            (propertyGet.target as PropertyGet).property = newField
        } else { // top-level field
            propertyGet.target = newField
        }
    }

    inner class TopLevelDelegatedFieldsAccessorVisitor : NodeVisitor {
        override fun visit(node: AstNode?): Boolean {
            if (node is FunctionNode && node.name.toString() in topLevelDelegatedFieldAccessorToOriginalField) {
                val accessorName = node.name.toString()
                val atomicField = topLevelDelegatedFieldAccessorToOriginalField[accessorName]!!
                // function get_topLevelDelegatedField() = a.value  -> _a.value
                // function set_topLevelDelegatedField(v: T) { a.value = v }  -> { _a.value = v }
                node.replaceAccessedField(accessorName.startsWith("get"), atomicField)
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
                        (node.parent as Assignment).right = valueNode
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
                    else if (node.target.toSource().matches(Regex(RECEIVER))) {
                        val receiverName = node.target.toSource() // $receiver_i
                        val rr = ReceiverResolver(receiverName)
                        node.enclosingFunction.visit(rr)
                        rr.receiver?.let { node.target = it }
                    }
                }
                // replace Atomic*Array.size call with `length` property on the pure type js array
                if (node.property.toSource() == ARRAY_SIZE) {
                    node.property = Name().also { it.identifier = LENGTH }
                }
            }
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
            if (node is Assignment && node.left is PropertyGet) {
                val left = node.left as PropertyGet
                if (traceFormatObjects.contains(left.target.toSource())) {
                    if (node.right is FunctionCall) {
                        // TraceFormatObject initialization
                        (node.right as FunctionCall).arguments = listOf(Name().also { it.identifier = "null" })
                    }
                }
            }
            // remove TraceFormatObject constructor definition
            if (node is FunctionNode && traceFormatObjects.contains(node.name)) {
                val body  = node.body
                for (stmt in body) { body.replaceChild(stmt, EmptyLine()) }
            }
            // remove TraceFormat from TraceFormatObject interfaces
            if (node is Assignment && node.left is PropertyGet && node.right is ObjectLiteral) {
                val left = node.left as PropertyGet
                val metadata = node.right as ObjectLiteral
                if (traceFormatObjects.contains(left.target.toSource())) {
                    for (e in metadata.elements) {
                        if (e.right is ArrayLiteral) {
                            val array = (e.right as ArrayLiteral).toSource()
                            if (array.contains(TRACE_FORMAT)) {
                                (e.right as ArrayLiteral).elements = emptyList()
                            }
                        }
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
    inner class ReceiverResolver(private val receiverName: String) : NodeVisitor {
        var receiver: AstNode? = null
        override fun visit(node: AstNode): Boolean {
            if (node is VariableInitializer) {
                if (node.target.toSource() == receiverName) {
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
                    if (field.toSource().matches(Regex(RECEIVER))) {
                        val receiverName = field.toSource() // $receiver_i
                        val rr = ReceiverResolver(receiverName)
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
            "atomicfu\$getAndSet" -> {
                val arg = args[0].toSource()
                "(function($SCOPE) {var oldValue = $f; $f = $arg; return oldValue;})()"
            }
            "atomicfu\$compareAndSet" -> {
                val expected = args[0].scopedSource()
                val updated = args[1].scopedSource()
                val equals = if (expected == "null") "==" else "==="
                "(function($SCOPE) {return $f $equals $expected ? function() { $f = $updated; return true }() : false})()"
            }
            "atomicfu\$getAndIncrement" -> {
                "(function($SCOPE) {return $f++;})()"
            }

            "atomicfu\$getAndIncrement\$long" -> {
                "(function($SCOPE) {var oldValue = $f; $f = $f.inc(); return oldValue;})()"
            }

            "atomicfu\$getAndDecrement" -> {
                "(function($SCOPE) {return $f--;})()"
            }

            "atomicfu\$getAndDecrement\$long" -> {
                "(function($SCOPE) {var oldValue = $f; $f = $f.dec(); return oldValue;})()"
            }

            "atomicfu\$getAndAdd" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {var oldValue = $f; $f += $arg; return oldValue;})()"
            }

            "atomicfu\$getAndAdd\$long" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {var oldValue = $f; $f = $f.add($arg); return oldValue;})()"
            }

            "atomicfu\$addAndGet" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {$f += $arg; return $f;})()"
            }

            "atomicfu\$addAndGet\$long" -> {
                val arg = args[0].scopedSource()
                "(function($SCOPE) {$f = $f.add($arg); return $f;})()"
            }

            "atomicfu\$incrementAndGet" -> {
                "(function($SCOPE) {return ++$f;})()"
            }

            "atomicfu\$incrementAndGet\$long" -> {
                "(function($SCOPE) {return $f = $f.inc();})()"
            }

            "atomicfu\$decrementAndGet" -> {
                "(function($SCOPE) {return --$f;})()"
            }

            "atomicfu\$decrementAndGet\$long" -> {
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

private class EmptyLine : EmptyExpression() {
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