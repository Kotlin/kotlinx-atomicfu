package kotlinx.atomicfu

import org.objectweb.asm.*
import org.objectweb.asm.ClassReader.SKIP_CODE
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.OBJECT
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.commons.InstructionAdapter.OBJECT_TYPE
import java.io.ByteArrayInputStream
import java.io.File

class TypeInfo(val fuType: Type, val pritiveType: Type)

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

class FieldInfo(fieldId: FieldId, fieldType: Type) {
    val className = fieldId.owner
    val fieldName = fieldId.name
    val fuName = fieldId.name + '$' + "FU"
    val typeInfo = AFU_TYPES[fieldType.internalName]!!
    val fuType = typeInfo.fuType
    val primitiveType = typeInfo.pritiveType
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
                    visitEnd()
                }
                transformed = true
                return null
            }
            return super.visitField(access, name, desc, signature, value)
        }

        override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            lastMethod = "$className::$name$desc"
            return TransformerMV(className, name, super.visitMethod(access, name, desc, signature, exceptions))
        }
    }

    private inner class TransformerMV(
        private val className: String,
        private val methodName: String,
        mv: MethodVisitor?
    ) : MethodVisitor(ASM5, mv) {
        override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
            val methodId = MethodId(owner, name, desc)
            when {
                opcode == Opcodes.INVOKESTATIC && methodId in FACTORIES -> {
                    info("$className: transforming factory $methodId invocation")
                    if (methodName != "<init>") error("$className: factory $methodId is used outside of constructor")
                    transformed = true
                }
                opcode == Opcodes.INVOKEVIRTUAL && owner in AFU_TYPES -> {
                    val typeInfo = AFU_TYPES[owner]!!
                    val fuName = when (name) {
                        "getValue" -> "get"
                        "setValue" -> "set"
                        else -> name
                    }
                    val methodType = Type.getMethodType(desc)
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, typeInfo.fuType.internalName, fuName,
                        Type.getMethodDescriptor(methodType.returnType, OBJECT_TYPE, *methodType.argumentTypes), false)
                    transformed = true
                }
                else -> super.visitMethodInsn(opcode, owner, name, desc, itf)
            }
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String?) {
            val fieldId = FieldId(owner, name)
            when {
                opcode == Opcodes.PUTFIELD && fieldId in fields -> {
                    if (methodName != "<init>") error("$className: initializing $fieldId outside of constructor")
                    val f = fields[fieldId]!!
                    if (f.primitiveType.sort == OBJECT) {
                        super.visitFieldInsn(Opcodes.PUTFIELD, owner, name, f.primitiveType.descriptor)
                    } else {
                        visitInsn(Opcodes.POP)
                    }
                    transformed = true
                }
                opcode == Opcodes.GETFIELD && fieldId in fields -> {
                    val f = fields[fieldId]!!
                    super.visitFieldInsn(Opcodes.GETSTATIC, owner, f.fuName, f.fuType.descriptor)
                    super.visitInsn(Opcodes.SWAP)
                    transformed = true
                }
                else -> super.visitFieldInsn(opcode, owner, name, desc)
            }
        }
    }
}