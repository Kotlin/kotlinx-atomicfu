/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package kotlinx.atomicfu.transformer

import org.objectweb.asm.*
import org.objectweb.asm.ClassReader.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.*
import org.objectweb.asm.commons.*
import org.objectweb.asm.commons.InstructionAdapter.*
import org.objectweb.asm.tree.*
import java.io.*
import java.net.*
import java.util.*

class TypeInfo(val fuType: Type, val originalType: Type, val transformedType: Type)

private const val AFU_PKG = "kotlinx/atomicfu"
private const val JUCA_PKG = "java/util/concurrent/atomic"
private const val JLI_PKG = "java/lang/invoke"
private const val ATOMIC = "atomic"

private val INT_ARRAY_TYPE = getType("[I")
private val LONG_ARRAY_TYPE = getType("[J")
private val BOOLEAN_ARRAY_TYPE = getType("[Z")
private val REF_ARRAY_TYPE = getType("[Ljava/lang/Object;")
private val REF_TYPE = getType("L$AFU_PKG/AtomicRef;")
private val ATOMIC_ARRAY_TYPE = getType("L$AFU_PKG/AtomicArray;")

private val AFU_CLASSES: Map<String, TypeInfo> = mapOf(
    "$AFU_PKG/AtomicInt" to TypeInfo(getObjectType("$JUCA_PKG/AtomicIntegerFieldUpdater"), INT_TYPE, INT_TYPE),
    "$AFU_PKG/AtomicLong" to TypeInfo(getObjectType("$JUCA_PKG/AtomicLongFieldUpdater"), LONG_TYPE, LONG_TYPE),
    "$AFU_PKG/AtomicRef" to TypeInfo(getObjectType("$JUCA_PKG/AtomicReferenceFieldUpdater"), OBJECT_TYPE, OBJECT_TYPE),
    "$AFU_PKG/AtomicBoolean" to TypeInfo(getObjectType("$JUCA_PKG/AtomicIntegerFieldUpdater"), BOOLEAN_TYPE, INT_TYPE),

    "$AFU_PKG/AtomicIntArray" to TypeInfo(getObjectType("$JUCA_PKG/AtomicIntegerArray"), INT_ARRAY_TYPE, INT_ARRAY_TYPE),
    "$AFU_PKG/AtomicLongArray" to TypeInfo(getObjectType("$JUCA_PKG/AtomicLongArray"), LONG_ARRAY_TYPE, LONG_ARRAY_TYPE),
    "$AFU_PKG/AtomicBooleanArray" to TypeInfo(getObjectType("$JUCA_PKG/AtomicIntegerArray"), BOOLEAN_ARRAY_TYPE, INT_ARRAY_TYPE),
    "$AFU_PKG/AtomicArray" to TypeInfo(getObjectType("$JUCA_PKG/AtomicReferenceArray"), REF_ARRAY_TYPE, REF_ARRAY_TYPE),
    "$AFU_PKG/AtomicFU_commonKt" to TypeInfo(getObjectType("$JUCA_PKG/AtomicReferenceArray"), REF_ARRAY_TYPE, REF_ARRAY_TYPE)
)

private val WRAPPER: Map<Type, String> = mapOf(
    INT_TYPE to "java/lang/Integer",
    LONG_TYPE to "java/lang/Long",
    BOOLEAN_TYPE to "java/lang/Boolean"
)

private val ARRAY_ELEMENT_TYPE: Map<Type, Int> = mapOf(
    INT_ARRAY_TYPE to T_INT,
    LONG_ARRAY_TYPE to T_LONG,
    BOOLEAN_ARRAY_TYPE to T_BOOLEAN
)

private val AFU_TYPES: Map<Type, TypeInfo> = AFU_CLASSES.mapKeys { getObjectType(it.key) }

private val METHOD_HANDLES = "$JLI_PKG/MethodHandles"
private val LOOKUP = "$METHOD_HANDLES\$Lookup"
private val VH_TYPE = getObjectType("$JLI_PKG/VarHandle")

private val STRING_TYPE = getObjectType("java/lang/String")
private val CLASS_TYPE = getObjectType("java/lang/Class")

private fun String.prettyStr() = replace('/', '.')

data class MethodId(val owner: String, val name: String, val desc: String, val invokeOpcode: Int) {
    override fun toString(): String = "${owner.prettyStr()}::$name"
}

private const val GET_VALUE = "getValue"
private const val SET_VALUE = "setValue"

private const val AFU_CLS = "$AFU_PKG/AtomicFU"

private val FACTORIES: Set<MethodId> = setOf(
    MethodId(AFU_CLS, ATOMIC, "(Ljava/lang/Object;)L$AFU_PKG/AtomicRef;", INVOKESTATIC),
    MethodId(AFU_CLS, ATOMIC, "(I)L$AFU_PKG/AtomicInt;", INVOKESTATIC),
    MethodId(AFU_CLS, ATOMIC, "(J)L$AFU_PKG/AtomicLong;", INVOKESTATIC),
    MethodId(AFU_CLS, ATOMIC, "(Z)L$AFU_PKG/AtomicBoolean;", INVOKESTATIC),

    MethodId("$AFU_PKG/AtomicIntArray", "<init>", "(I)V", INVOKESPECIAL),
    MethodId("$AFU_PKG/AtomicLongArray", "<init>", "(I)V", INVOKESPECIAL),
    MethodId("$AFU_PKG/AtomicBooleanArray", "<init>", "(I)V", INVOKESPECIAL),
    MethodId("$AFU_PKG/AtomicArray", "<init>", "(I)V", INVOKESPECIAL),
    MethodId("$AFU_PKG/AtomicFU_commonKt", "atomicArrayOfNulls", "(I)L$AFU_PKG/AtomicArray;", INVOKESTATIC)
)

private operator fun Int.contains(bit: Int) = this and bit != 0

private inline fun code(mv: MethodVisitor, block: InstructionAdapter.() -> Unit) {
    block(InstructionAdapter(mv))
}

private inline fun insns(block: InstructionAdapter.() -> Unit): InsnList {
    val node = MethodNode(ASM5)
    block(InstructionAdapter(node))
    return node.instructions
}

data class FieldId(val owner: String, val name: String, val desc: String) {
    override fun toString(): String = "${owner.prettyStr()}::$name"
}

class FieldInfo(
    val fieldId: FieldId,
    val fieldType: Type,
    val isStatic: Boolean = false
) {
    val owner = fieldId.owner
    val ownerType: Type = getObjectType(owner)
    val typeInfo = AFU_CLASSES.getValue(fieldType.internalName)
    val fuType = typeInfo.fuType
    val isArray = typeInfo.originalType.sort == ARRAY

    // state: updated during analysis
    val accessors = mutableSetOf<MethodId>() // set of accessor method that read the corresponding atomic
    var hasExternalAccess = false // accessed from different package
    var hasAtomicOps = false // has atomic operations operations other than getValue/setValue

    val name: String
        get() = if (hasExternalAccess) mangleInternal(fieldId.name) else fieldId.name
    val fuName: String
        get() {
            val fuName = fieldId.name + '$' + "FU"
            return if (hasExternalAccess) mangleInternal(fuName) else fuName
        }

    val refVolatileClassName = "${owner.replace('.', '/')}${name.capitalize()}RefVolatile"
    val staticRefVolatileField = refVolatileClassName.substringAfterLast("/").decapitalize()

    fun getPrimitiveType(vh: Boolean): Type = if (vh) typeInfo.originalType else typeInfo.transformedType

    private fun mangleInternal(fieldName: String): String = "$fieldName\$internal"

    override fun toString(): String = "${owner.prettyStr()}::$name"
}

enum class Variant { FU, VH, BOTH }

class AtomicFUTransformer(
    classpath: List<String>,
    inputDir: File,
    outputDir: File = inputDir,
    var variant: Variant = Variant.FU
) : AtomicFUTransformerBase(inputDir, outputDir) {

    private val classPathLoader = URLClassLoader(
        (listOf(inputDir) + (classpath.map { File(it) } - outputDir))
            .map { it.toURI().toURL() }.toTypedArray()
    )

    private val fields = mutableMapOf<FieldId, FieldInfo>()
    private val accessors = mutableMapOf<MethodId, FieldInfo>()
    private val removeMethods = mutableSetOf<MethodId>()

    override fun transform() {
        info("Analyzing in $inputDir")
        val files = inputDir.walk().filter { it.isFile }.toList()
        val needTransform = analyzeFilesForFields(files)
        if (needTransform || outputDir == inputDir) {
            val vh = variant == Variant.VH
            // visit method bodies for external references to fields, runs all logic, fails if anything is wrong
            val needsTransform = analyzeFilesForRefs(files, vh)
            // perform transformation
            info("Transforming to $outputDir")
            files.forEach { file ->
                val bytes = file.readBytes()
                val outBytes = if (file.isClassFile() && file in needsTransform) transformFile(file, bytes, vh) else bytes
                val outFile = file.toOutputFile()
                outFile.mkdirsAndWrite(outBytes)
                if (variant == Variant.BOTH && outBytes !== bytes) {
                    val vhBytes = transformFile(file, bytes, true)
                    val vhFile = outputDir / "META-INF" / "versions" / "9" / file.relativeTo(inputDir).toString()
                    vhFile.mkdirsAndWrite(vhBytes)
                }
            }
        } else {
            info("Nothing to transform -- all classes are up to date")
        }
    }

    // Phase 1: visit methods and fields, register all accessors, collect times
    // Returns 'true' if any files are out of date
    private fun analyzeFilesForFields(files: List<File>): Boolean {
        var needTransform = false
        files.forEach { file ->
            val inpTime = file.lastModified()
            val outTime = file.toOutputFile().lastModified()
            if (inpTime > outTime) needTransform = true
            if (file.isClassFile()) analyzeFileForFields(file)
        }
        if (lastError != null) throw TransformerException("Encountered errors while analyzing fields", lastError)
        return needTransform
    }

    private fun analyzeFileForFields(file: File) {
        file.inputStream().use { ClassReader(it).accept(FieldsCollectorCV(), SKIP_FRAMES) }
    }

    // Phase2: visit method bodies for external references to fields and
    //          run method analysis in "analysisMode" to see which fields need AU/VH generated for them
    // Returns a set of files that need transformation
    private fun analyzeFilesForRefs(files: List<File>, vh: Boolean): Set<File> {
        val result = HashSet<File>()
        files.forEach { file ->
            if (file.isClassFile() && analyzeFileForRefs(file, vh)) result += file
        }
        // Batch analyze all files, report all errors, bail out only at the end
        if (lastError != null) throw TransformerException("Encountered errors while analyzing references", lastError)
        return result
    }

    private fun analyzeFileForRefs(file: File, vh: Boolean): Boolean =
        file.inputStream().use { input ->
            transformed = false // clear global "transformed" flag
            val cv = TransformerCV(null, vh, analyzePhase2 = true)
            try {
                ClassReader(input).accept(cv, SKIP_FRAMES)
            } catch (e: Exception) {
                error("Failed to analyze: $e", cv.sourceInfo)
                e.printStackTrace(System.out)
                if (lastError == null) lastError = e
            }
            transformed // true for classes that need transformation
        }

    // Phase 3: Transform file (only called for files that need to be transformed)
    // Returns updated byte array for class data
    private fun transformFile(file: File, bytes: ByteArray, vh: Boolean): ByteArray {
        transformed = false // clear global "transformed" flag
        val cw = CW()
        val cv = TransformerCV(cw, vh, analyzePhase2 = false)
        try {
            ClassReader(ByteArrayInputStream(bytes)).accept(cv, SKIP_FRAMES)
        } catch (e: Exception) {
            error("Failed to transform: $e", cv.sourceInfo)
            e.printStackTrace(System.out)
            if (lastError == null) lastError = e
        }
        if (!transformed) error("Invoked transformFile on a file that does not need transformation: $file")
        if (lastError != null) throw TransformerException("Encountered errors while transforming: $file", lastError)
        info("Transformed $file")
        return cw.toByteArray() // write transformed bytes
    }

    private abstract inner class CV(cv: ClassVisitor?) : ClassVisitor(ASM5, cv) {
        lateinit var className: String

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            className = name
            super.visit(version, access, name, signature, superName, interfaces)
        }
    }

    private fun registerField(field: FieldId, fieldType: Type, isStatic: Boolean): FieldInfo {
        val result = fields.getOrPut(field) { FieldInfo(field, fieldType, isStatic) }
        if (result.fieldType != fieldType) abort("$field type mismatch between $fieldType and ${result.fieldType}")
        return result
    }

    private inner class FieldsCollectorCV : CV(null) {
        override fun visitField(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            val fieldType = getType(desc)
            if (fieldType.sort == OBJECT && fieldType.internalName in AFU_CLASSES) {
                val field = FieldId(className, name, desc)
                info("$field field found")
                if (ACC_PUBLIC in access) error("$field field cannot be public")
                if (ACC_FINAL !in access) error("$field field must be final")
                registerField(field, fieldType, (ACC_STATIC in access))
            }
            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            val methodType = getMethodType(desc)
            if (methodType.argumentTypes.any { it in AFU_TYPES }) {
                val methodId = MethodId(className, name, desc, accessToInvokeOpcode(access))
                info("$methodId method to be removed")
                removeMethods += methodId
            }
            getPotentialAccessorType(access, className, methodType)?.let { onType ->
                return AccessorCollectorMV(onType.internalName, access, name, desc, signature, exceptions)
            }
            return null
        }
    }

    private inner class AccessorCollectorMV(
        private val className: String,
        access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?
    ) : MethodNode(ASM5, access, name, desc, signature, exceptions) {
        override fun visitEnd() {
            val insns = instructions.listUseful(4)
            if (insns.size == 3 &&
                insns[0].isAload(0) &&
                insns[1].isGetField(className) &&
                insns[2].isAreturn() ||
                insns.size == 2 &&
                insns[0].isGetStatic(className) &&
                insns[1].isAreturn()
            ) {
                val isStatic = insns.size == 2
                val fi = (if (isStatic) insns[0] else insns[1]) as FieldInsnNode
                val fieldName = fi.name
                val field = FieldId(className, fieldName, fi.desc)
                val fieldType = getType(fi.desc)
                val accessorMethod = MethodId(className, name, desc, accessToInvokeOpcode(access))
                info("$field accessor $name found")
                val fieldInfo = registerField(field, fieldType, isStatic)
                fieldInfo.accessors += accessorMethod
                accessors[accessorMethod] = fieldInfo
            }
        }
    }

    // returns a type on which this is a potential accessor
    private fun getPotentialAccessorType(access: Int, className: String, methodType: Type): Type? {
        if (methodType.returnType !in AFU_TYPES) return null
        return if (access and ACC_STATIC != 0) {
            if (access and ACC_FINAL != 0 && methodType.argumentTypes.isEmpty()) {
                // accessor for top-level atomic
                getObjectType(className)
            } else {
                // accessor for top-level atomic
                if (methodType.argumentTypes.size == 1 && methodType.argumentTypes[0].sort == OBJECT)
                    methodType.argumentTypes[0] else null
            }
        } else {
            // if it not static, then it must be final
            if (access and ACC_FINAL != 0 && methodType.argumentTypes.isEmpty())
                getObjectType(className) else null
        }
    }

    private fun descToName(desc: String): String = desc.drop(1).dropLast(1)

    private inner class TransformerCV(
        cv: ClassVisitor?,
        private val vh: Boolean,
        private val analyzePhase2: Boolean // true in Phase 2 when we are analyzing file for refs (not transforming yet)
    ) : CV(cv) {
        private var source: String? = null
        var sourceInfo: SourceInfo? = null

        private var metadata: AnnotationNode? = null

        private var originalClinit: MethodNode? = null
        private var newClinit: MethodNode? = null

        private fun newClinit() = MethodNode(ASM5, ACC_STATIC, "<clinit>", "()V", null, null)
        fun getOrCreateNewClinit(): MethodNode = newClinit ?: newClinit().also { newClinit = it }

        override fun visitSource(source: String?, debug: String?) {
            this.source = source
            super.visitSource(source, debug)
        }

        override fun visitField(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            val fieldType = getType(desc)
            if (fieldType.sort == OBJECT && fieldType.internalName in AFU_CLASSES) {
                val fieldId = FieldId(className, name, desc)
                val f = fields[fieldId]!!
                val protection = when {
                    // reference to wrapper class (primitive atomics) or reference to to j.u.c.a.Atomic*Array (atomic array)
                    f.isStatic && !vh -> ACC_STATIC or ACC_FINAL or ACC_SYNTHETIC
                    // primitive type field
                    f.isStatic && vh -> ACC_STATIC or ACC_SYNTHETIC
                    f.hasExternalAccess -> ACC_PUBLIC or ACC_SYNTHETIC
                    f.accessors.isEmpty() -> ACC_PRIVATE
                    else -> 0
                }
                val primitiveType = f.getPrimitiveType(vh)
                val fv = when {
                    // replace (top-level) Atomic*Array with (static) j.u.c.a/Atomic*Array field
                    f.isArray && !vh -> super.visitField(protection, f.name, f.fuType.descriptor, null, null)
                    // replace top-level primitive atomics with static instance of the corresponding wrapping *RefVolatile class
                    f.isStatic && !vh -> super.visitField(
                        protection,
                        f.staticRefVolatileField,
                        getObjectType(f.refVolatileClassName).descriptor,
                        null,
                        null
                    )
                    // volatile primitive type field
                    else -> super.visitField(protection or ACC_VOLATILE, f.name, primitiveType.descriptor, null, null)
                }
                if (vh) {
                    // VarHandle is needed for all array element accesses and for regular fields with atomic ops
                    if (f.hasAtomicOps || f.isArray) vhField(protection, f)
                } else {
                    // FieldUpdater is not needed for arrays (they use AtomicArrays)
                    if (f.hasAtomicOps && !f.isArray) fuField(protection, f)
                }
                transformed = true
                return fv
            }
            return super.visitField(access, name, desc, signature, value)
        }

        // Generates static VarHandle field
        private fun vhField(protection: Int, f: FieldInfo) {
            super.visitField(protection or ACC_FINAL or ACC_STATIC, f.fuName, VH_TYPE.descriptor, null, null)
            code(getOrCreateNewClinit()) {
                if (!f.isArray) {
                    invokestatic(METHOD_HANDLES, "lookup", "()L$LOOKUP;", false)
                    aconst(getObjectType(className))
                    aconst(f.name)
                    val primitiveType = f.getPrimitiveType(vh)
                    if (primitiveType.sort == OBJECT) {
                        aconst(primitiveType)
                    } else {
                        val wrapper = WRAPPER.getValue(primitiveType)
                        getstatic(wrapper, "TYPE", CLASS_TYPE.descriptor)
                    }
                    val findVHName = if (f.isStatic) "findStaticVarHandle" else "findVarHandle"
                    invokevirtual(
                        LOOKUP, findVHName,
                        getMethodDescriptor(VH_TYPE, CLASS_TYPE, STRING_TYPE, CLASS_TYPE), false
                    )
                    putstatic(className, f.fuName, VH_TYPE.descriptor)
                } else {
                    // create VarHandle for array
                    aconst(f.getPrimitiveType(vh))
                    invokestatic(
                        METHOD_HANDLES,
                        "arrayElementVarHandle",
                        getMethodDescriptor(VH_TYPE, CLASS_TYPE),
                        false
                    )
                    putstatic(className, f.fuName, VH_TYPE.descriptor)
                }
            }
        }

        // Generates static AtomicXXXFieldUpdater field
        private fun fuField(protection: Int, f: FieldInfo) {
            super.visitField(protection or ACC_FINAL or ACC_STATIC, f.fuName, f.fuType.descriptor, null, null)
            code(getOrCreateNewClinit()) {
                val params = mutableListOf<Type>()
                params += CLASS_TYPE
                if (!f.isStatic) aconst(getObjectType(className)) else aconst(getObjectType(f.refVolatileClassName))
                val primitiveType = f.getPrimitiveType(vh)
                if (primitiveType.sort == OBJECT) {
                    params += CLASS_TYPE
                    aconst(primitiveType)
                }
                params += STRING_TYPE
                aconst(f.name)
                invokestatic(
                    f.fuType.internalName,
                    "newUpdater",
                    getMethodDescriptor(f.fuType, *params.toTypedArray()),
                    false
                )
                putstatic(className, f.fuName, f.fuType.descriptor)
            }
        }

        override fun visitMethod(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            val methodId = MethodId(className, name, desc, accessToInvokeOpcode(access))
            if (methodId in accessors || methodId in removeMethods) {
                // drop and skip the methods that were found in Phase 1
                // todo: should remove those methods from kotlin metadata, too
                transformed = true
                return null
            }
            val sourceInfo = SourceInfo(methodId, source)
            val superMV = if (name == "<clinit>" && desc == "()V") {
                if (access and ACC_STATIC == 0) abort("<clinit> method not marked as static")
                // defer writing class initialization method
                val node = MethodNode(ASM5, access, name, desc, signature, exceptions)
                if (originalClinit != null) abort("Multiple <clinit> methods found")
                originalClinit = node
                node
            } else {
                // write transformed method to class right away
                super.visitMethod(access, name, desc, signature, exceptions)
            }
            val mv = TransformerMV(
                sourceInfo, access, name, desc, signature, exceptions, superMV,
                className.ownerPackageName, vh, analyzePhase2
            )
            this.sourceInfo = mv.sourceInfo
            return mv
        }

        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
            if (desc == KOTLIN_METADATA_DESC) {
                check(visible) { "Expected run-time visible $KOTLIN_METADATA_DESC annotation" }
                check(metadata == null) { "Only one $KOTLIN_METADATA_DESC annotation is expected" }
                return AnnotationNode(desc).also { metadata = it }
            }
            return super.visitAnnotation(desc, visible)
        }

        override fun visitEnd() {
            // remove unused methods from metadata
            metadata?.let {
                val mt = MetadataTransformer(
                    removeFields = fields.keys,
                    removeMethods = accessors.keys + removeMethods
                )
                if (mt.transformMetadata(it)) transformed = true
                if (cv != null) it.accept(cv.visitAnnotation(KOTLIN_METADATA_DESC, true))
            }
            if (analyzePhase2) return // nop in analyze phase
            // collect class initialization
            if (originalClinit != null || newClinit != null) {
                val newClinit = newClinit
                if (newClinit == null) {
                    // dump just original clinit
                    originalClinit!!.accept(cv)
                } else {
                    // create dummy base code if needed
                    val originalClinit = originalClinit ?: newClinit().also {
                        code(it) { visitInsn(RETURN) }
                    }
                    // makes sure return is last useful instruction
                    val last = originalClinit.instructions.last
                    val ret = last.thisOrPrevUseful
                    if (ret == null || !ret.isReturn()) abort("Last instruction in <clinit> shall be RETURN", ret)
                    originalClinit.instructions.insertBefore(ret, newClinit.instructions)
                    originalClinit.accept(cv)
                }
            }
            super.visitEnd()
        }
    }

    private inner class TransformerMV(
        sourceInfo: SourceInfo,
        access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?,
        mv: MethodVisitor?,
        private val packageName: String,
        private val vh: Boolean,
        private val analyzePhase2: Boolean // true in Phase 2 when we are analyzing file for refs (not transforming yet)
    ) : MethodNode(ASM5, access, name, desc, signature, exceptions) {
        init {
            this.mv = mv
        }

        val sourceInfo = sourceInfo.copy(insnList = instructions)

        private var tempLocal = 0
        private var bumpedLocals = 0

        private fun bumpLocals(n: Int) {
            if (bumpedLocals == 0) tempLocal = maxLocals
            while (n > bumpedLocals) bumpedLocals = n
            maxLocals = tempLocal + bumpedLocals
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
            val methodId = MethodId(owner, name, desc, opcode)
            val fieldInfo = accessors[methodId]
            // compare owner packages
            if (fieldInfo != null && methodId.owner.ownerPackageName != packageName) {
                if (analyzePhase2) {
                    fieldInfo.hasExternalAccess = true
                } else {
                    check(fieldInfo.hasExternalAccess) // should have been set on previous phase
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf)
        }

        override fun visitEnd() {
            // transform instructions list
            var hasErrors = false
            var i = instructions.first
            while (i != null)
                try {
                    i = transform(i)
                } catch (e: AbortTransform) {
                    error(e.message!!, sourceInfo.copy(i = e.i))
                    i = i.next
                    hasErrors = true
                }
            // save transformed method if not in analysis phase
            if (!hasErrors && !analyzePhase2)
                accept(mv)
        }

        private fun FieldInsnNode.checkPutFieldOrPutStatic(): FieldId? {
            if (opcode != PUTFIELD && opcode != PUTSTATIC) return null
            val fieldId = FieldId(owner, name, desc)
            return if (fieldId in fields) fieldId else null
        }

        // ld: instruction that loads atomic field (already changed to getstatic)
        // iv: invoke virtual on the loaded atomic field (to be fixed)
        private fun fixupInvokeVirtual(
            ld: FieldInsnNode,
            onArrayElement: Boolean, // true when fixing invokeVirtual on loaded array element
            iv: MethodInsnNode,
            f: FieldInfo
        ): AbstractInsnNode? {
            check(f.isArray || !onArrayElement) { "Cannot fix array element access on non array fields" }
            val typeInfo = if (onArrayElement) f.typeInfo else AFU_CLASSES.getValue(iv.owner)
            if (iv.name == GET_VALUE || iv.name == SET_VALUE) {
                check(!f.isArray || onArrayElement) { "getValue/setValue can only be called on elements of arrays" }
                val setInsn = iv.name == SET_VALUE
                if (!onArrayElement) {
                    instructions.remove(ld) // drop getstatic (we don't need field updater)
                    val primitiveType = f.getPrimitiveType(vh)
                    val owner = if (!vh && f.isStatic) f.refVolatileClassName else f.owner
                    if (!vh && f.isStatic) {
                        val getOwnerClass = FieldInsnNode(
                            GETSTATIC,
                            f.owner,
                            f.staticRefVolatileField,
                            getObjectType(owner).descriptor
                        )
                        instructions.insertBefore(iv, getOwnerClass)
                    }
                    val j = FieldInsnNode(
                        when {
                            iv.name == GET_VALUE -> if (f.isStatic && vh) GETSTATIC else GETFIELD
                            else -> if (f.isStatic && vh) PUTSTATIC else PUTFIELD
                        }, owner, f.name, primitiveType.descriptor
                    )
                    instructions.set(iv, j) // replace invokevirtual with get/setfield
                    return j.next
                } else {
                    var methodType = getMethodType(iv.desc)
                    if (f.typeInfo.originalType != f.typeInfo.transformedType && !vh) {
                        val ret = f.typeInfo.transformedType.elementType
                        iv.desc = if (setInsn) getMethodDescriptor(methodType.returnType, ret) else getMethodDescriptor(ret, *methodType.argumentTypes)
                        methodType = getMethodType(iv.desc)
                    }
                    iv.name = iv.name.substring(0, 3)
                    if (!vh) {
                        // map to j.u.c.a.Atomic*Array get or set
                        iv.owner = descToName(f.fuType.descriptor)
                        iv.desc = getMethodDescriptor(methodType.returnType, INT_TYPE, *methodType.argumentTypes)
                    } else {
                        // map to VarHandle get or set
                        iv.owner = descToName(VH_TYPE.descriptor)
                        iv.desc = getMethodDescriptor(
                            methodType.returnType,
                            f.getPrimitiveType(vh),
                            INT_TYPE,
                            *methodType.argumentTypes
                        )
                    }
                    return iv
                }
            }
            // An operation other than getValue/setValue is used
            if (f.isArray && iv.name == "get") { // "operator get" that retrieves array element, further ops apply to it
                // fixup atomic operation on this array element
                return fixupLoadedArrayElement(f, ld, iv)
            } else {
                // non-trivial atomic operation
                check(f.isArray == onArrayElement) { "Atomic operations can be performed on atomic elements only" }
                if (analyzePhase2) {
                    f.hasAtomicOps = true // mark the fact that non-trivial atomic op is used here
                } else {
                    check(f.hasAtomicOps) // should have been set on previous phase
                }
                // update method invocation
                if (vh) {
                    vhOperation(iv, typeInfo, f)
                } else {
                    fuOperation(iv, typeInfo, f)
                }
                if (f.isStatic && !onArrayElement) {
                    if (!vh) {
                        // getstatic *RefVolatile class
                        val aload = FieldInsnNode(
                            GETSTATIC,
                            f.owner,
                            f.staticRefVolatileField,
                            getObjectType(f.refVolatileClassName).descriptor
                        )
                        instructions.insert(ld, aload)
                    }
                    return iv.next
                }
                if (!onArrayElement) {
                    // insert swap after field load
                    val swap = InsnNode(SWAP)
                    instructions.insert(ld, swap)
                    return swap.next
                }
                return iv.next
            }
        }

        private fun vhOperation(iv: MethodInsnNode, typeInfo: TypeInfo, f: FieldInfo) {
            val methodType = getMethodType(iv.desc)
            val args = methodType.argumentTypes
            iv.owner = VH_TYPE.internalName
            val params = if (!f.isArray && !f.isStatic) mutableListOf<Type>(
                OBJECT_TYPE,
                *args
            ) else if (!f.isArray && f.isStatic) mutableListOf<Type>(*args) else mutableListOf(
                typeInfo.originalType,
                INT_TYPE,
                *args
            )
            val elementType = if (f.isArray) typeInfo.originalType.elementType else typeInfo.originalType
            val long = elementType == LONG_TYPE
            when (iv.name) {
                "lazySet" -> iv.name = "setRelease"
                "getAndIncrement" -> {
                    instructions.insertBefore(iv, insns { if (long) lconst(1) else iconst(1) })
                    params += elementType
                    iv.name = "getAndAdd"
                }
                "getAndDecrement" -> {
                    instructions.insertBefore(iv, insns { if (long) lconst(-1) else iconst(-1) })
                    params += elementType
                    iv.name = "getAndAdd"
                }
                "addAndGet" -> {
                    bumpLocals(if (long) 2 else 1)
                    instructions.insertBefore(iv, insns {
                        if (long) dup2() else dup()
                        store(tempLocal, elementType)
                    })
                    iv.name = "getAndAdd"
                    instructions.insert(iv, insns {
                        load(tempLocal, elementType)
                        add(elementType)
                    })
                }
                "incrementAndGet" -> {
                    instructions.insertBefore(iv, insns { if (long) lconst(1) else iconst(1) })
                    params += elementType
                    iv.name = "getAndAdd"
                    instructions.insert(iv, insns {
                        if (long) lconst(1) else iconst(1)
                        add(elementType)
                    })
                }
                "decrementAndGet" -> {
                    instructions.insertBefore(iv, insns { if (long) lconst(-1) else iconst(-1) })
                    params += elementType
                    iv.name = "getAndAdd"
                    instructions.insert(iv, insns {
                        if (long) lconst(-1) else iconst(-1)
                        add(elementType)
                    })
                }
            }
            iv.desc = getMethodDescriptor(methodType.returnType, *params.toTypedArray())
        }

        private fun fuOperation(iv: MethodInsnNode, typeInfo: TypeInfo, f: FieldInfo) {
            val methodType = getMethodType(iv.desc)
            val originalElementType = if (f.isArray) typeInfo.originalType.elementType else typeInfo.originalType
            val transformedElementType =
                if (f.isArray) typeInfo.transformedType.elementType else typeInfo.transformedType
            val trans = originalElementType != transformedElementType
            val args = methodType.argumentTypes
            var ret = methodType.returnType
            if (trans) {
                args.forEachIndexed { i, type -> if (type == originalElementType) args[i] = transformedElementType }
                if (iv.name == "getAndSet") ret = transformedElementType
            }
            if (f.isArray) {
                // map to j.u.c.a.AtomicIntegerArray method
                iv.owner = typeInfo.fuType.internalName
                // add int argument as element index
                iv.desc = getMethodDescriptor(ret, INT_TYPE, *args)
                return // array operation in this mode does not use FU field
            }
            iv.owner = typeInfo.fuType.internalName
            iv.desc = getMethodDescriptor(ret, OBJECT_TYPE, *args)
        }

        private fun tryEraseUncheckedCast(getter: AbstractInsnNode) {
            if (getter.next.opcode == DUP && getter.next.next.opcode == IFNONNULL) {
                // unchecked cast upon AtomicRef var is performed
                // erase compiler check for this var being not null:
                // (remove all insns from ld till the non null branch label)
                val ifnonnull = (getter.next.next as JumpInsnNode)
                var i: AbstractInsnNode = getter.next
                while (!(i is LabelNode && i.label == ifnonnull.label.label)) {
                    val next = i.next
                    instructions.remove(i)
                    i = next
                }
            }
        }

        private fun fixupLoadedAtomicVar(f: FieldInfo, ld: FieldInsnNode): AbstractInsnNode? {
            if (f.fieldType == REF_TYPE) tryEraseUncheckedCast(ld)
            val j = FlowAnalyzer(ld.next).execute()
            return fixupOperationOnAtomicVar(j, f, ld, null)
        }

        private fun fixupLoadedArrayElement(f: FieldInfo, ld: FieldInsnNode, getter: MethodInsnNode): AbstractInsnNode? {
            if (f.fieldType == ATOMIC_ARRAY_TYPE) tryEraseUncheckedCast(getter)
            // contains array field load (in vh case: + swap and pure type array load) and array element index
            // this array element information is only used in case the reference to this element is stored (copied and inserted at the point of loading)
            val arrayElementInfo = mutableListOf<AbstractInsnNode>()
            if (vh) {
                arrayElementInfo.add(ld.previous.previous) // getstatic VarHandle field
                arrayElementInfo.add(ld.previous) // swap
            }
            var i: AbstractInsnNode = ld
            while (i != getter) {
                arrayElementInfo.add(i)
                i = i.next
            }
            // start of array element operation arguments
            val args = getter.next
            // remove array element getter
            instructions.remove(getter)
            val arrayElementOperation = FlowAnalyzer(args).execute()
            return fixupOperationOnAtomicVar(arrayElementOperation, f, ld, arrayElementInfo)
        }

        private fun fixupOperationOnAtomicVar(operation: AbstractInsnNode, f: FieldInfo, ld: FieldInsnNode, arrayElementInfo: List<AbstractInsnNode>?): AbstractInsnNode? {
            when (operation) {
                is MethodInsnNode -> {
                    // invoked virtual method on atomic var -- fixup & done with it
                    debug("invoke $f.${operation.name}", sourceInfo.copy(i = operation))
                    return fixupInvokeVirtual(ld, arrayElementInfo != null, operation, f)
                }
                is VarInsnNode -> {
                    val onArrayElement = arrayElementInfo != null
                    check(f.isArray == onArrayElement)
                    // was stored to local -- needs more processing:
                    // store owner ref into the variable instead
                    val v = operation.`var`
                    val next = operation.next
                    if (onArrayElement) {
                        // leave just owner class load insn on stack
                        arrayElementInfo!!.forEach { instructions.remove(it) }
                    } else {
                        instructions.remove(ld)
                    }
                    val lv = localVar(v, operation)
                    if (lv != null) {
                        // Stored to a local variable with an entry in LVT (typically because of inline function)
                        if (lv.desc != f.fieldType.descriptor && !onArrayElement)
                            abort("field $f was stored to a local variable #$v \"${lv.name}\" with unexpected type: ${lv.desc}")
                        // correct local variable descriptor
                        lv.desc = f.ownerType.descriptor
                        lv.signature = null
                        // process all loads of this variable in the corresponding local variable range
                        forVarLoads(v, lv.start, lv.end) { otherLd ->
                            fixupLoad(f, ld, otherLd, arrayElementInfo)
                        }
                    } else {
                        // Spilled temporarily to a local variable w/o an entry in LVT -> fixup only one load
                        fixupLoad(f, ld, nextVarLoad(v, next), arrayElementInfo)
                    }
                    return next
                }
                else -> abort("cannot happen")
            }
        }

        private fun fixupLoad(f: FieldInfo, ld: FieldInsnNode, otherLd: VarInsnNode, arrayElementInfo: List<AbstractInsnNode>?): AbstractInsnNode? =
            if (arrayElementInfo != null) {
                fixupArrayElementLoad(f, ld, otherLd, arrayElementInfo)
            } else {
                fixupVarLoad(f, ld, otherLd)
            }

        private fun fixupVarLoad(f: FieldInfo, ld: FieldInsnNode, otherLd: VarInsnNode): AbstractInsnNode? {
            val ldCopy = ld.clone(null) as FieldInsnNode
            instructions.insert(otherLd, ldCopy)
            return fixupLoadedAtomicVar(f, ldCopy)
        }

        private fun fixupArrayElementLoad(f: FieldInfo, ld: FieldInsnNode, otherLd: VarInsnNode, arrayElementInfo: List<AbstractInsnNode>): AbstractInsnNode? {
            if (f.fieldType == ATOMIC_ARRAY_TYPE) tryEraseUncheckedCast(otherLd)
            // index instructions from array element info: drop owner class load instruction (in vh case together with preceding getting VH + swap)
            val index = arrayElementInfo.drop(if (vh) 3 else 1)
            // previously stored array element reference is loaded -> arrayElementInfo should be cloned and inserted at the point of this load
            // before cloning make sure that index instructions contain just loads and simple arithmetic, without any invocations and complex data flow
            for (indexInsn in index) {
                checkDataFlowComplexity(indexInsn)
            }
            // start of atomic operation arguments
            val args = otherLd.next
            val operationOnArrayElement = FlowAnalyzer(args).execute()
            val arrayElementInfoCopy = mutableListOf<AbstractInsnNode>()
            arrayElementInfo.forEach { arrayElementInfoCopy.add(it.clone(null)) }
            arrayElementInfoCopy.forEach { instructions.insertBefore(args, it) }
            return fixupOperationOnAtomicVar(operationOnArrayElement, f, ld, arrayElementInfo)
        }

        fun checkDataFlowComplexity(i: AbstractInsnNode) {
            when (i) {
                is MethodInsnNode -> {
                    abort("No method invocations are allowed for calculation of an array element index " +
                        "at the point of loading the reference to this element.\n" +
                        "Extract index calculation to the local variable.", i)
                }
                is LdcInsnNode -> { /* ok loading const */ }
                else -> {
                    when(i.opcode) {
                        IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR -> { /* simple arithmetics */ }
                        ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5, ILOAD, IALOAD -> { /* int loads */ }
                        GETFIELD, GETSTATIC -> { /* getting fields */ }
                        else -> {
                            abort("Complex data flow is not allowed for calculation of an array element index " +
                                "at the point of loading the reference to this element.\n" +
                                "Extract index calculation to the local variable.", i)
                        }
                    }
                }
            }
        }

        private fun putPrimitiveTypeWrapper(
            factoryInsn: MethodInsnNode,
            f: FieldInfo,
            next: FieldInsnNode
        ): AbstractInsnNode? {
            // generate wrapper class for static fields of primitive type
            val factoryArg = getMethodType(factoryInsn.desc).argumentTypes[0]
            generateRefVolatileClass(f, factoryArg)
            val firstInitInsn = FlowAnalyzer(next).getInitStart()
            // remove calling atomic factory for static field and following putstatic
            val afterPutStatic = next.next
            instructions.remove(factoryInsn)
            instructions.remove(next)
            initRefVolatile(f, factoryArg, firstInitInsn, afterPutStatic)
            return afterPutStatic
        }

        private fun putJucaAtomicArray(
            arrayfactoryInsn: MethodInsnNode,
            f: FieldInfo,
            next: FieldInsnNode
        ): AbstractInsnNode? {
            // replace with invoking j.u.c.a.Atomic*Array constructor
            val jucaAtomicArrayDesc = f.typeInfo.fuType.descriptor
            val initStart = FlowAnalyzer(next).getInitStart().next
            if (initStart.opcode == NEW) {
                // change descriptor of NEW instruction
                (initStart as TypeInsnNode).desc = descToName(jucaAtomicArrayDesc)
                arrayfactoryInsn.owner = descToName(jucaAtomicArrayDesc)
            } else {
                // array initialisation starts from bipush size, then static array factory was called (atomicArrayOfNulls)
                // add NEW j.u.c.a.Atomic*Array instruction
                val newInsn = TypeInsnNode(NEW, descToName(jucaAtomicArrayDesc))
                instructions.insert(initStart.previous, newInsn)
                instructions.insert(newInsn, InsnNode(DUP))
                val jucaArrayFactory =
                    MethodInsnNode(INVOKESPECIAL, descToName(jucaAtomicArrayDesc), "<init>", "(I)V", false)
                instructions.set(arrayfactoryInsn, jucaArrayFactory)
            }
            //fix the following putfield
            next.desc = jucaAtomicArrayDesc
            next.name = f.name
            transformed = true
            return next.next
        }

        private fun putPureVhArray(
            arrayFactoryInsn: MethodInsnNode,
            f: FieldInfo,
            next: FieldInsnNode
        ): AbstractInsnNode? {
            val initStart = FlowAnalyzer(next).getInitStart().next
            if (initStart.opcode == NEW) {
                // remove dup
                instructions.remove(initStart.next)
                // remove NEW AFU_PKG/Atomic*Array instruction
                instructions.remove(initStart)
            }
            // create pure array of given size and put it
            val primitiveType = f.getPrimitiveType(vh)
            val primitiveElementType = ARRAY_ELEMENT_TYPE[f.typeInfo.originalType]
            val newArray =
                if (primitiveElementType != null) IntInsnNode(NEWARRAY, primitiveElementType)
                else TypeInsnNode(ANEWARRAY, descToName(primitiveType.elementType.descriptor))
            instructions.set(arrayFactoryInsn, newArray)
            next.desc = primitiveType.descriptor
            next.name = f.name
            transformed = true
            return next.next
        }

        private fun transform(i: AbstractInsnNode): AbstractInsnNode? {
            when (i) {
                is MethodInsnNode -> {
                    val methodId = MethodId(i.owner, i.name, i.desc, i.opcode)
                    when {
                        methodId in FACTORIES -> {
                            if (name != "<init>" && name != "<clinit>") abort("factory $methodId is used outside of constructor or class initialisation")
                            val next = i.nextUseful
                            val fieldId = (next as? FieldInsnNode)?.checkPutFieldOrPutStatic()
                                ?: abort("factory $methodId invocation must be followed by putfield")
                            val f = fields[fieldId]!!
                            // in FU mode wrap values of top-level primitive atomics into corresponding *RefVolatile class
                            if (!vh && f.isStatic && !f.isArray) {
                                return putPrimitiveTypeWrapper(i, f, next)
                            }
                            if (f.isArray) {
                                return if (vh) {
                                    putPureVhArray(i, f, next)
                                } else {
                                    putJucaAtomicArray(i, f, next)
                                }
                            }
                            instructions.remove(i)
                            transformed = true
                            val primitiveType = f.getPrimitiveType(vh)
                            next.desc = primitiveType.descriptor
                            next.name = f.name
                            return next.next
                        }
                        methodId in accessors -> {
                            // replace INVOKESTATIC/VIRTUAL to accessor with GETSTATIC on var handle / field updater
                            val f = accessors[methodId]!!
                            val j = FieldInsnNode(
                                GETSTATIC, f.owner, f.fuName,
                                if (vh) VH_TYPE.descriptor else f.fuType.descriptor
                            )
                            // set original name for an array in FU mode
                            if (!vh && f.isArray) {
                                j.opcode = if (!f.isStatic) GETFIELD else GETSTATIC
                                j.name = f.name
                            }
                            instructions.set(i, j)
                            if (vh && f.isArray) {
                                return insertPureVhArray(j, f)
                            }
                            transformed = true
                            return fixupLoadedAtomicVar(f, j)
                        }
                        methodId in removeMethods -> {
                            abort(
                                "invocation of method $methodId on atomic types. " +
                                        "Make the later method 'inline' to use it", i
                            )
                        }
                        i.opcode == INVOKEVIRTUAL && i.owner in AFU_CLASSES -> {
                            abort("standalone invocation of $methodId that was not traced to previous field load", i)
                        }
                    }
                }
                is FieldInsnNode -> {
                    val fieldId = FieldId(i.owner, i.name, i.desc)
                    if ((i.opcode == GETFIELD || i.opcode == GETSTATIC) && fieldId in fields) {
                        // Convert GETFIELD to GETSTATIC on var handle / field updater
                        val f = fields[fieldId]!!
                        val isArray = f.getPrimitiveType(vh).sort == ARRAY
                        // GETSTATIC for all fields except FU arrays
                        if (!isArray || vh) {
                            if (i.desc != f.fieldType.descriptor) return i.next // already converted get/setfield
                            i.opcode = GETSTATIC
                            i.name = f.fuName
                        }
                        // for FU arrays with external access change name to mangled one
                        if (!vh && isArray && f.hasExternalAccess) {
                            i.name = f.name
                        }
                        i.desc = if (vh) VH_TYPE.descriptor else f.fuType.descriptor
                        if (vh && f.getPrimitiveType(vh).sort == ARRAY) {
                            return insertPureVhArray(i, f)
                        }
                        transformed = true
                        return fixupLoadedAtomicVar(f, i)
                    }
                }
            }
            return i.next
        }

        private fun insertPureVhArray(getVarHandleInsn: FieldInsnNode, f: FieldInfo): AbstractInsnNode? {
            val getPureArray = FieldInsnNode(GETFIELD, f.owner, f.name, f.getPrimitiveType(vh).descriptor)
            if (!f.isStatic) {
                // swap className reference and VarHandle
                val swap = InsnNode(SWAP)
                instructions.insert(getVarHandleInsn, swap)
                instructions.insert(swap, getPureArray)
            } else {
                getPureArray.opcode = GETSTATIC
                instructions.insert(getVarHandleInsn, getPureArray)
            }
            transformed = true
            return fixupLoadedAtomicVar(f, getPureArray)
        }

        // generates a ref class with volatile field of primitive type inside
        private fun generateRefVolatileClass(f: FieldInfo, arg: Type) {
            if (analyzePhase2) return // nop
            val cw = ClassWriter(0)
            cw.visit(V1_6, ACC_PUBLIC or ACC_SYNTHETIC, f.refVolatileClassName, null, "java/lang/Object", null)
            //creating class constructor
            val cons = cw.visitMethod(ACC_PUBLIC, "<init>", "(${arg.descriptor})V", null, null)
            code(cons) {
                visitVarInsn(ALOAD, 0)
                invokespecial("java/lang/Object", "<init>", "()V", false)
                visitVarInsn(ALOAD, 0)
                load(1, arg)
                putfield(f.refVolatileClassName, f.name, f.getPrimitiveType(vh).descriptor)
                visitInsn(RETURN)
                // stack size to fit long type
                visitMaxs(3, 3)
            }
            //declaring volatile field of primitive type
            val protection = ACC_VOLATILE
            cw.visitField(protection, f.name, f.getPrimitiveType(vh).descriptor, null, null)
            val genFile = outputDir / "${f.refVolatileClassName}.class"
            genFile.mkdirsAndWrite(cw.toByteArray())
        }

        // Initializes static instance of generated *RefVolatile class
        private fun initRefVolatile(
            f: FieldInfo,
            argType: Type,
            firstInitInsn: AbstractInsnNode,
            lastInitInsn: AbstractInsnNode
        ) {
            val new = TypeInsnNode(NEW, f.refVolatileClassName)
            val dup = InsnNode(DUP)
            instructions.insertBefore(firstInitInsn, new)
            instructions.insertBefore(firstInitInsn, dup)
            val invokespecial =
                MethodInsnNode(INVOKESPECIAL, f.refVolatileClassName, "<init>", "(${argType.descriptor})V", false)
            val putstatic = FieldInsnNode(
                PUTSTATIC,
                f.owner,
                f.staticRefVolatileField,
                getObjectType(f.refVolatileClassName).descriptor
            )
            instructions.insertBefore(lastInitInsn, invokespecial)
            instructions.insert(invokespecial, putstatic)
        }
    }

    private inner class CW : ClassWriter(COMPUTE_MAXS or COMPUTE_FRAMES) {
        override fun getCommonSuperClass(type1: String, type2: String): String {
            var c: Class<*> = loadClass(type1)
            val d: Class<*> = loadClass(type2)
            if (c.isAssignableFrom(d)) return type1
            if (d.isAssignableFrom(c)) return type2
            return if (c.isInterface || d.isInterface) {
                "java/lang/Object"
            } else {
                do {
                    c = c.superclass
                } while (!c.isAssignableFrom(d))
                c.name.replace('.', '/')
            }
        }
    }

    private fun loadClass(type: String): Class<*> =
        try {
            Class.forName(type.replace('/', '.'), false, classPathLoader)
        } catch (e: Exception) {
            throw TransformerException("Failed to load class for '$type'", e)
        }
}

fun main(args: Array<String>) {
    if (args.size !in 1..3) {
        println("Usage: AtomicFUTransformerKt <dir> [<output>] [<variant>]")
        return
    }
    val t = AtomicFUTransformer(emptyList(), File(args[0]))
    if (args.size > 1) t.outputDir = File(args[1])
    if (args.size > 2) t.variant = enumValueOf(args[2].toUpperCase(Locale.US))
    t.verbose = true
    t.transform()
}