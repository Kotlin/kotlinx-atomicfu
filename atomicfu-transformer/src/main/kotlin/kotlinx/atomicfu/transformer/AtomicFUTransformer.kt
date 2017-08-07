@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package kotlinx.atomicfu.transformer

import org.objectweb.asm.*
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.OBJECT
import org.objectweb.asm.Type.getMethodDescriptor
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.commons.InstructionAdapter.OBJECT_TYPE
import org.objectweb.asm.tree.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLClassLoader

class TypeInfo(val fuType: Type, val primitiveType: Type)

private const val AFU_PKG = "kotlinx/atomicfu"
private const val JUCA_PKG = "java/util/concurrent/atomic"
private const val ATOMIC = "atomic"

private val AFU_CLASSES: Map<String, TypeInfo> = mapOf(
        "$AFU_PKG/AtomicInt" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicIntegerFieldUpdater"), Type.INT_TYPE),
        "$AFU_PKG/AtomicLong" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicLongFieldUpdater"), Type.LONG_TYPE),
        "$AFU_PKG/AtomicRef" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicReferenceFieldUpdater"), OBJECT_TYPE)
    )

private val AFU_TYPES: Map<Type, TypeInfo> = AFU_CLASSES.mapKeys { Type.getObjectType(it.key) }

private fun String.prettyStr() = replace('/', '.')

data class MethodId(val owner: String, val name: String, val desc: String) {
    override fun toString(): String = "${owner.prettyStr()}::$name"
}

private const val AFU_CLS = "$AFU_PKG/AtomicFU"

private val FACTORIES: Set<MethodId> = setOf(
        MethodId(AFU_CLS, ATOMIC, "(Ljava/lang/Object;)L$AFU_PKG/AtomicRef;"),
        MethodId(AFU_CLS, ATOMIC, "(I)L$AFU_PKG/AtomicInt;"),
        MethodId(AFU_CLS, ATOMIC, "(J)L$AFU_PKG/AtomicLong;")
    )

private fun File.isClassFile() = toString().endsWith(".class")

private operator fun Int.contains(bit: Int) = this and bit != 0

private inline fun code(mv: MethodVisitor, block: InstructionAdapter.() -> Unit) {
    block(InstructionAdapter(mv))
}

data class FieldId(val owner: String, val name: String) {
    override fun toString(): String = "${owner.prettyStr()}::$name"
}

class FieldInfo(fieldId: FieldId, val fieldType: Type) {
    val owner = fieldId.owner
    val name = fieldId.name
    val fuName = fieldId.name + '$' + "FU"
    val typeInfo = AFU_CLASSES[fieldType.internalName]!!
    val fuType = typeInfo.fuType
    val primitiveType = typeInfo.primitiveType
    val accessors = mutableListOf<MethodId>()
    override fun toString(): String = "${owner.prettyStr()}::$name"
}

data class SourceInfo(
    val method: MethodId,
    val source: String?,
    val i: AbstractInsnNode? = null,
    val insnList: InsnList? = null
) {
    override fun toString(): String = buildString {
        source?.let { append("$it:") }
        i?.line?.let { append("$it:") }
        append(" $method")
    }
}

class AtomicFUTransformer(
    classpath: List<String>,
    private val inputDir: File,
    private val outputDir: File = inputDir
) {
    var verbose = false

    private var logger = LoggerFactory.getLogger(AtomicFUTransformer::class.java)

    private val classLoader = URLClassLoader(
        (listOf(inputDir) + (classpath.map { File(it) } - outputDir))
            .map {it.toURI().toURL() }.toTypedArray())

    private var hasErrors = false
    private var transformed = false

    private val fields = mutableMapOf<FieldId, FieldInfo>()
    private val accessors = mutableMapOf<MethodId, FieldInfo>()

    private fun format(message: String, sourceInfo: SourceInfo? = null): String {
        var loc = if (sourceInfo == null) "" else sourceInfo.toString() + ": "
        if (verbose && sourceInfo != null && sourceInfo.i != null)
            loc += sourceInfo.i.atIndex(sourceInfo.insnList)
        return "$loc$message"
    }

    private fun info(message: String, sourceInfo: SourceInfo? = null) {
        logger.info(format(message, sourceInfo))
    }

    private fun debug(message: String, sourceInfo: SourceInfo? = null) {
        logger.debug(format(message, sourceInfo))
    }

    private fun error(message: String, sourceInfo: SourceInfo? = null) {
        logger.error(format(message, sourceInfo))
        hasErrors = true
    }

    fun transform() {
        info("Analyzing in $inputDir")
        var inpFilesTime = 0L
        var outFilesTime = 0L
        inputDir.walk().filter { it.isFile }.forEach { file ->
            inpFilesTime = inpFilesTime.coerceAtLeast(file.lastModified())
            if (file.isClassFile()) analyzeFile(file)
            outFilesTime = outFilesTime.coerceAtLeast(file.toOutputFile().lastModified())
        }
        if (hasErrors) throw Exception("Encountered errors while collecting fields")
        if (inpFilesTime > outFilesTime || outputDir == inputDir) {
            // perform transformation
            info("Transforming to $outputDir")
            inputDir.walk().filter { it.isFile }.forEach { file ->
                val bytes = file.readBytes()
                val outBytes = if (file.isClassFile()) transformFile(file, bytes) else bytes
                val outFile = file.toOutputFile()
                outFile.parentFile.mkdirs()
                outFile.writeBytes(outBytes) // write resulting bytes
            }
            if (hasErrors) throw Exception("Encountered errors while transforming")
        } else {
            info("Nothing to transform -- all classes are up to date")
        }
    }

    private fun File.toOutputFile(): File =
        File(outputDir, relativeTo(inputDir).toString())

    private fun analyzeFile(file: File) {
        ClassReader(file.inputStream()).accept(CollectorCV(), SKIP_FRAMES)
    }

    private fun transformFile(file: File, bytes: ByteArray): ByteArray {
        transformed = false
        val cw = CW()
        val cv = TransformerCV(cw)
        try {
            ClassReader(ByteArrayInputStream(bytes)).accept(cv, SKIP_FRAMES)
            if (transformed && !hasErrors) {
                info("Transformed $file")
                return cw.toByteArray() // write transformed bytes
            }
        } catch (e: Exception) {
            error("Failed to transform: $e", cv.sourceInfo)
            e.printStackTrace(System.out)
        }
        return bytes
    }

    private abstract inner class CV(cv: ClassVisitor?) : ClassVisitor(ASM5, cv) {
        lateinit var className: String

        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            className = name
            super.visit(version, access, name, signature, superName, interfaces)
        }
    }

    private fun registerField(field: FieldId, fieldType: Type): FieldInfo {
        val result = fields.getOrPut(field) { FieldInfo(field, fieldType) }
        if (result.fieldType != fieldType) abort("$field type mismatch between $fieldType and ${result.fieldType}")
        return result
    }

    private inner class CollectorCV : CV(null) {
        override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
            val fieldType = Type.getType(desc)
            if (fieldType.sort == OBJECT && fieldType.internalName in AFU_CLASSES) {
                val field = FieldId(className, name)
                info("$field field found")
                if (ACC_PUBLIC in access) error("$field field cannot be public")
                if (ACC_FINAL !in access) error("$field field must be final")
                registerField(field, fieldType)
            }
            return null
        }

        override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            val methodType = Type.getMethodType(desc)
            if (isPotentialAccessorMethod(access, methodType))
                return AccessorCollectorMV(methodType.argumentTypes[0].internalName, access, name, desc, signature, exceptions)
            return null
        }
    }

    private fun isPotentialAccessorMethod(access: Int, methodType: Type): Boolean =
        access or ACC_SYNTHETIC != 0 &&
        access or ACC_STATIC != 0 &&
        methodType.argumentTypes.size == 1 &&
        methodType.argumentTypes[0].sort == OBJECT &&
        methodType.returnType in AFU_TYPES

    private inner class AccessorCollectorMV(
        private val className: String,
        access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?
    ) : MethodNode(ASM5, access, name, desc, signature, exceptions) {
        override fun visitEnd() {
            val insns = instructions.listUseful(4)
            if (insns.size == 3 &&
                insns[0].isAload(0) &&
                insns[1].isGetField(className) &&
                insns[2].isAreturn())
            {
                val fi = insns[1] as FieldInsnNode
                val fieldName = fi.name
                val field = FieldId(className, fieldName)
                val fieldType = Type.getType(fi.desc)
                val accessorMethod = MethodId(className, name, desc)
                info("$field accessor $name found")
                val fieldInfo = registerField(field, fieldType)
                fieldInfo.accessors += accessorMethod
                accessors[accessorMethod] = fieldInfo
            }
        }
    }

    private inner class TransformerCV(cv: ClassVisitor) : CV(cv) {
        private var source: String? = null
        var sourceInfo: SourceInfo? = null

        override fun visitSource(source: String?, debug: String?) {
            this.source = source
            super.visitSource(source, debug)
        }

        override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
            val fieldType = Type.getType(desc)
            if (fieldType.sort == OBJECT && fieldType.internalName in AFU_CLASSES) {
                val f = fields[FieldId(className, name)]!!
                val protection = if (f.accessors.isEmpty()) ACC_PRIVATE else 0
                super.visitField(protection or ACC_VOLATILE, f.name, f.primitiveType.descriptor, null, null)
                super.visitField(protection or ACC_FINAL or ACC_STATIC, f.fuName, f.fuType.descriptor, null, null)
                code(super.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null)) {
                    visitCode()
                    val params = mutableListOf<Type>()
                    params += Type.getObjectType("java/lang/Class")
                    aconst(Type.getObjectType(className))
                    if (f.primitiveType.sort == OBJECT) {
                        params += Type.getObjectType("java/lang/Class")
                        aconst(f.primitiveType)
                    }
                    params += Type.getObjectType("java/lang/String")
                    aconst(f.name)
                    invokestatic(f.fuType.internalName, "newUpdater", Type.getMethodDescriptor(f.fuType, *params.toTypedArray()), false)
                    putstatic(className, f.fuName, f.fuType.descriptor)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                transformed = true
                return null
            }
            return super.visitField(access, name, desc, signature, value)
        }

        override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            val method = MethodId(className, name, desc)
            if (method in accessors)
                return null // drop accessor
            val sourceInfo = SourceInfo(method, source)
            val superMV = super.visitMethod(access, name, desc, signature, exceptions)
            val mv = TransformerMV(sourceInfo, access, name, desc, signature, exceptions, superMV)
            this.sourceInfo = mv.sourceInfo
            return mv
        }
    }

    private inner class TransformerMV(
        sourceInfo: SourceInfo,
        access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?,
        mv: MethodVisitor
    ) : MethodNode(ASM5, access, name, desc, signature, exceptions) {
        init { this.mv = mv }

        val sourceInfo = sourceInfo.copy(insnList = instructions)

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
            // save transformed method
            if (!hasErrors)
                accept(mv)
        }

        private fun FieldInsnNode.checkPutField(): FieldId? {
            if (opcode != Opcodes.PUTFIELD) return null
            val fieldId = FieldId(owner, name)
            return if (fieldId in fields) fieldId else null
        }

        // ld: instruction that loads atomic field (already changed to getstatic)
        // iv: invoke virtual on the loaded atomic field (to be fixed)
        private fun fixupInvokeVirtual(ld: FieldInsnNode, iv: MethodInsnNode, f: FieldInfo): AbstractInsnNode? {
            val typeInfo = AFU_CLASSES[iv.owner]!!
            if (iv.name == "getValue" || iv.name == "setValue") {
                instructions.remove(ld) // drop getstatic (we don't need field updater)
                val j = FieldInsnNode(
                    if (iv.name == "getValue") GETFIELD else PUTFIELD,
                    f.owner, f.name, f.primitiveType.descriptor)
                instructions.set(iv, j) // replace invokevirtual with get/setfield
                return j.next
            }
            // update method invocation to FieldUpdater invocation
            val methodType = Type.getMethodType(iv.desc)
            iv.owner = typeInfo.fuType.internalName
            iv.desc = getMethodDescriptor(methodType.returnType, OBJECT_TYPE, *methodType.argumentTypes)
            // insert swap after field load
            val swap = InsnNode(SWAP)
            instructions.insert(ld, swap)
            return swap.next
        }

        private fun fixupLoadedAtomicVar(ld: FieldInsnNode, f: FieldInfo): AbstractInsnNode? {
            val j = FlowAnalyzer(ld.next).execute()
            when (j) {
                is MethodInsnNode -> {
                    // invoked virtual method on atomic var -- fixup & done with it
                    debug("invoke $f.${j.name}", sourceInfo.copy(i = j))
                    return fixupInvokeVirtual(ld, j, f)
                }
                is VarInsnNode -> {
                    // was stored to local -- needs more processing:
                    // store owner ref into the variable instead
                    val v = j.`var`
                    val next = j.next
                    instructions.remove(ld)
                    localVariables[v]!!.apply {
                        desc = f.owner
                        signature = null
                    }
                    // recursively process all loads of this variable
                    forVarLoads(v) { otherLd ->
                        val ldCopy = ld.clone(null) as FieldInsnNode
                        instructions.insert(otherLd, ldCopy)
                        fixupLoadedAtomicVar(ldCopy, f)
                    }
                    return next
                }
                else -> abort("cannot happen")
            }
        }

        private fun transform(i: AbstractInsnNode): AbstractInsnNode? {
            when (i) {
                is MethodInsnNode -> {
                    val methodId = MethodId(i.owner, i.name, i.desc)
                    when {
                        i.opcode == INVOKESTATIC && methodId in FACTORIES -> {
                            if (name != "<init>") abort("factory $methodId is used outside of constructor")
                            val next = i.nextUseful
                            val fieldId = (next as? FieldInsnNode)?.checkPutField() ?:
                                abort("factory $methodId invocation must be followed by putfield")
                            instructions.remove(i)
                            transformed = true
                            val f = fields[fieldId]!!
                            (next as FieldInsnNode).desc = f.primitiveType.descriptor
                            return next.next
                        }
                        i.opcode == INVOKESTATIC && methodId in accessors -> {
                            // replace GETSTATIC to accessor with GETSTATIC on field updater
                            val f = accessors[methodId]!!
                            val j = FieldInsnNode(GETSTATIC, f.owner, f.fuName, f.fuType.descriptor)
                            instructions.insert(i, j)
                            instructions.remove(i)
                            transformed = true
                            return fixupLoadedAtomicVar(j, f)
                        }
                        i.opcode == INVOKEVIRTUAL && i.owner in AFU_CLASSES -> {
                            abort("standalone invocation of $methodId that was not traced to previous field load", i)
                        }
                    }
                }
                is FieldInsnNode -> {
                    val fieldId = FieldId(i.owner, i.name)
                    if (i.opcode == GETFIELD && fieldId in fields) {
                        // Convert GETFIELD to GETSTATIC on field updater
                        val f = fields[fieldId]!!
                        if (i.desc != f.fieldType.descriptor) return i.next // already converted get/setfield
                        i.opcode = GETSTATIC
                        i.name = f.fuName
                        i.desc = f.fuType.descriptor
                        transformed = true
                        return fixupLoadedAtomicVar(i, f)
                    }
                }
            }
            return i.next
        }
    }

    private inner class CW : ClassWriter(COMPUTE_MAXS or COMPUTE_FRAMES) {
        override fun getCommonSuperClass(type1: String, type2: String): String {
            var c: Class<*> = Class.forName(type1.replace('/', '.'), false, classLoader)
            val d: Class<*> = Class.forName(type2.replace('/', '.'), false, classLoader)
            if (c.isAssignableFrom(d)) return type1
            if (d.isAssignableFrom(c)) return type2
            if (c.isInterface || d.isInterface) {
                return "java/lang/Object"
            } else {
                do {
                    c = c.superclass
                } while (!c.isAssignableFrom(d))
                return c.name.replace('.', '/')
            }
        }
    }
}

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: AtomicFUTransformerKt <dir>")
        return
    }
    val t = AtomicFUTransformer(emptyList(), File(args[0]))
    t.verbose = true
    t.transform()
}