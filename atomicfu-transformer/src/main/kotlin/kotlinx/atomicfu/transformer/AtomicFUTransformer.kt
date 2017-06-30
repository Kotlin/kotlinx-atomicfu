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
import kotlin.coroutines.experimental.buildSequence

class TypeInfo(val fuType: Type, val primitiveType: Type)

private const val AFU_PKG = "kotlinx/atomicfu"
private const val JUCA_PKG = "java/util/concurrent/atomic"

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
        MethodId(AFU_CLS, "atomicInt", "()L$AFU_PKG/AtomicInt;"),
        MethodId(AFU_CLS, "atomicLong", "()L$AFU_PKG/AtomicLong;"),
        MethodId(AFU_CLS, "atomic", "()L$AFU_PKG/AtomicRef;"),
        MethodId(AFU_CLS, "atomic", "(Ljava/lang/Object;)L$AFU_PKG/AtomicRef;")
    )

private operator fun Int.contains(bit: Int) = this and bit != 0
private infix fun Int.wo(bit: Int) = this and bit.inv()

private inline fun code(mv: MethodVisitor, block: InstructionAdapter.() -> Unit) {
    block(InstructionAdapter(mv))
}

data class FieldId(val owner: String, val name: String) {
    override fun toString(): String = "$owner::$name"
}

private class AbortTransform(msg: String) : Exception(msg)

private fun abort(msg: String): Nothing = throw AbortTransform(msg)

class FieldInfo(fieldId: FieldId, fieldType: Type) {
    val fieldName = fieldId.name
    val fuName = fieldId.name + '$' + "FU"
    val typeInfo = AFU_TYPES[fieldType.internalName]!!
    val fuType = typeInfo.fuType
    val primitiveType = typeInfo.primitiveType
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
            if (transformed) {
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
                super.visitField((access wo ACC_FINAL) or ACC_VOLATILE, f.fieldName, f.primitiveType.descriptor, null, null)
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
                    aconst(f.fieldName)
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
            var i = instructions.first
            while (i != null)
                try {
                    i = transform(i)
                } catch (e: AbortTransform) {
                    error("$className::$name: ${e.message}")
                    i = i.next
                }
            // save transformed method
            accept(mv)
        }

        fun AbstractInsnNode?.skipLL(): AbstractInsnNode? {
            var cur = this
            while (cur is LabelNode || cur is LineNumberNode) cur = cur.next
            return cur
        }

        fun AbstractInsnNode?.skipStoreLoad(): AbstractInsnNode? {
            val a = skipLL()
            if (a !is VarInsnNode || a.opcode != Opcodes.ASTORE) return null
            val b = a.next.skipLL()
            if (b !is VarInsnNode || b.opcode != Opcodes.ALOAD || b.`var` != a.`var`) return null
            return b
        }

        fun AbstractInsnNode?.sameVarLoads(): Sequence<AbstractInsnNode> = buildSequence {
            val ld = this@sameVarLoads ?: return@buildSequence
            ld as VarInsnNode // type assertion
            var cur = ld.next
            while (cur != null) {
                if (cur is VarInsnNode && cur.opcode == Opcodes.ALOAD && cur.`var` == ld.`var`) yield(cur)
                cur = cur.next
            }
        }

        fun FieldInsnNode.checkPutField(): FieldId? {
            if (opcode != Opcodes.PUTFIELD) return null
            val fieldId = FieldId(owner, name)
            return if (fieldId in fields) fieldId else null
        }

        fun transform(i: AbstractInsnNode): AbstractInsnNode? {
            when (i) {
                is MethodInsnNode -> {
                    val methodId = MethodId(i.owner, i.name, i.desc)
                    when {
                        i.opcode == Opcodes.INVOKESTATIC && methodId in FACTORIES -> {
                            if (name != "<init>") abort("factory $methodId is used outside of constructor")
                            val next = i.next.skipLL()
                            val fieldId = (next as? FieldInsnNode)?.checkPutField() ?:
                                abort("factory $methodId invocation must be followed by putfield")
                            instructions.remove(i)
                            transformed = true
                            val f = fields[fieldId]!!
                            if (Type.getType(i.desc).argumentTypes.isNotEmpty()) {
                                (next as FieldInsnNode).desc = f.primitiveType.descriptor
                                return next.next
                            } else {
                                val pop = InsnNode(Opcodes.POP)
                                instructions.insert(next, pop)
                                instructions.remove(next)
                                return pop.next
                            }
                        }
                        i.opcode == Opcodes.INVOKEVIRTUAL && i.owner in AFU_TYPES -> {
                            val typeInfo = AFU_TYPES[i.owner]!!
                            val fuName = when (i.name) {
                                "getValue" -> "get"
                                "setValue" -> "set"
                                else -> i.name
                            }
                            val methodType = Type.getMethodType(i.desc)
                            i.owner = typeInfo.fuType.internalName
                            i.name = fuName
                            i.desc = getMethodDescriptor(methodType.returnType, OBJECT_TYPE, *methodType.argumentTypes)
                            transformed = true
                        }
                    }
                }
                is FieldInsnNode -> {
                    val fieldId = FieldId(i.owner, i.name)
                    when {
                        i.opcode == Opcodes.GETFIELD && fieldId in fields -> {
                            val f = fields[fieldId]!!
                            i.opcode = Opcodes.GETSTATIC
                            i.name = f.fuName
                            i.desc = f.fuType.descriptor
                            transformed = true
                            val swap = InsnNode(Opcodes.SWAP)
                            val after = i.next.skipStoreLoad()
                            if (after != null) {
                                instructions.remove(i)
                                instructions.insert(after, i)
                            }
                            instructions.insert(i, swap)
                            for (j in after.sameVarLoads()) {
                                val i2 = i.clone(null)
                                instructions.insert(j, i2)
                                instructions.insert(i2, InsnNode(Opcodes.SWAP))
                            }
                            return swap.next
                        }
                        fieldId in fields -> abort("Unsupported operation on $fieldId")
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