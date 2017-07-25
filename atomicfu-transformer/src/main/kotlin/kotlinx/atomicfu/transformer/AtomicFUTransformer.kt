@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package kotlinx.atomicfu.transformer

import org.objectweb.asm.*
import org.objectweb.asm.ClassReader.SKIP_CODE
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.OBJECT
import org.objectweb.asm.Type.getMethodDescriptor
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.commons.InstructionAdapter.OBJECT_TYPE
import org.objectweb.asm.tree.*
import java.io.ByteArrayInputStream
import java.io.File

class TypeInfo(val fuType: Type, val primitiveType: Type)

private const val AFU_PKG = "kotlinx/atomicfu"
private const val JUCA_PKG = "java/util/concurrent/atomic"
private const val ATOMIC = "atomic"

private val AFU_TYPES: Map<String, TypeInfo> = mapOf(
        "$AFU_PKG/AtomicInt" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicIntegerFieldUpdater"), Type.INT_TYPE),
        "$AFU_PKG/AtomicLong" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicLongFieldUpdater"), Type.LONG_TYPE),
        "$AFU_PKG/AtomicRef" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicReferenceFieldUpdater"), OBJECT_TYPE)
    )

data class MethodId(val owner: String, val name: String, val desc: String) {
    override fun toString(): String = "$owner::$name"
}

private const val AFU_CLS = "$AFU_PKG/AtomicFU"

private val FACTORIES: Set<MethodId> = setOf(
        MethodId(AFU_CLS, ATOMIC, "(Ljava/lang/Object;)L$AFU_PKG/AtomicRef;"),
        MethodId(AFU_CLS, ATOMIC, "(I)L$AFU_PKG/AtomicInt;"),
        MethodId(AFU_CLS, ATOMIC, "(J)L$AFU_PKG/AtomicLong;")
    )

private operator fun Int.contains(bit: Int) = this and bit != 0
private infix fun Int.wo(bit: Int) = this and bit.inv()

private inline fun code(mv: MethodVisitor, block: InstructionAdapter.() -> Unit) {
    block(InstructionAdapter(mv))
}

data class FieldId(val owner: String, val name: String) {
    override fun toString(): String = "$owner::$name"
}

class FieldInfo(fieldId: FieldId, val fieldType: Type) {
    val owner = fieldId.owner
    val name = fieldId.name
    val fuName = fieldId.name + '$' + "FU"
    val typeInfo = AFU_TYPES[fieldType.internalName]!!
    val fuType = typeInfo.fuType
    val primitiveType = typeInfo.primitiveType
    override fun toString(): String = "$owner::$name"
}

class AtomicFUTransformer(private val dir: File) {
    private var hasErrors = false
    private var transformed = false

    private val fields = mutableMapOf<FieldId, FieldInfo>()

    private fun walkClassFiles() = dir.walk()
        .filter { it.name.endsWith(".class") }

    private fun info(msg: String) {
        println("AtomicFU: $msg")
    }

    private fun debug(msg: String) {
        println("AtomicFU: debug: $msg")
    }

    private fun error(msg: String) {
        println("AtomicFU: ERROR: $msg")
        hasErrors = true
    }

    fun transform() {
        info("Analyzing $dir...")
        walkClassFiles().forEach { collectFields(it) }
        if (hasErrors) throw Exception("Encountered errors while collecting fields")
        info("Transforming $dir...")
        walkClassFiles().forEach { transformFile(it) }
        if (hasErrors) throw Exception("Encountered errors while transforming")
    }

    private fun collectFields(file: File) {
        ClassReader(file.inputStream()).accept(CollectorCV(), SKIP_CODE)
    }

    private fun transformFile(file: File) {
        transformed = false
        val bytes = file.readBytes()
        val cw = ClassWriter(COMPUTE_MAXS or COMPUTE_FRAMES)
        val cv = TransformerCV(cw)
        try {
            ClassReader(ByteArrayInputStream(bytes)).accept(cv, SKIP_FRAMES)
            if (transformed && !hasErrors) {
                info("Writing transformed $file")
                file.writeBytes(cw.toByteArray())
            }
        } catch (e: Exception) {
            error("${cv.lastMethod ?: cv.className}: Failed to transform: $e")
            e.printStackTrace(System.out)
        }
    }

    private abstract inner class CV(cv: ClassVisitor?) : ClassVisitor(ASM5, cv) {
        lateinit var className: String

        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            className = name
            super.visit(version, access, name, signature, superName, interfaces)
        }
    }

    private inner class CollectorCV : CV(null) {
        override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
            val fieldType = Type.getType(desc)
            if (fieldType.sort == OBJECT && fieldType.internalName in AFU_TYPES) {
                val field = FieldId(className, name)
                info("$field field found")
                if (ACC_PUBLIC in access) error("$field field cannot be public")
                if (ACC_FINAL !in access) error("$field field must be final")
                fields[field] = FieldInfo(field, fieldType)
            }
            return super.visitField(access, name, desc, signature, value)
        }
    }

    private inner class TransformerCV(cv: ClassVisitor) : CV(cv) {
        var lastMethod: String? = null

        override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
            val fieldType = Type.getType(desc)
            if (fieldType.sort == OBJECT && fieldType.internalName in AFU_TYPES) {
                val f = fields[FieldId(className, name)]!!
                super.visitField((access wo ACC_FINAL) or ACC_VOLATILE, f.name, f.primitiveType.descriptor, null, null)
                super.visitField(access or ACC_STATIC, f.fuName, f.fuType.descriptor, null, null)
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
            lastMethod = "$className::$name$desc"
            return TransformerMV(className, access, name, desc, signature, exceptions, super.visitMethod(access, name, desc, signature, exceptions))
        }
    }

    private inner class TransformerMV(
        private val className: String,
        access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?,
        mv: MethodVisitor
    ) : MethodNode(ASM5, access, name, desc, signature, exceptions) {
        init { this.mv = mv }

        override fun visitEnd() {
            // transform instructions list
            var hasErrors = false
            var i = instructions.first
            while (i != null)
                try {
                    i = transform(i)
                } catch (e: AbortTransform) {
                    error("$className::$name: ${e.message}")
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
            val typeInfo = AFU_TYPES[iv.owner]!!
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
            val j = FlowAnalyzer(ld.next, instructions).execute()
            when (j) {
                is MethodInsnNode -> {
                    // invoked virtual method on atomic var -- fixup & done with it
                    debug("${j.atLine()}$className::$name -- $f.${j.name}")
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
                        i.opcode == Opcodes.INVOKESTATIC && methodId in FACTORIES -> {
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
                        i.opcode == Opcodes.INVOKEVIRTUAL && i.owner in AFU_TYPES -> {
                            abort("standalone invocation of $methodId that was not traced to previous field load", i, instructions)
                        }
                    }
                }
                is FieldInsnNode -> {
                    val fieldId = FieldId(i.owner, i.name)
                    if (i.opcode == Opcodes.GETFIELD && fieldId in fields) {
                        // Convert getfield to getstatic
                        val f = fields[fieldId]!!
                        if (i.desc != f.fieldType.descriptor) return i.next // already converted get/setfield
                        i.opcode = Opcodes.GETSTATIC
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
}

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: AtomicFUTransformerKt <dir>")
        return
    }
    AtomicFUTransformer(File(args[0])).transform()
}