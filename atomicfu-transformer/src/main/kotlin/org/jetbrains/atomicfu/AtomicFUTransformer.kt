package org.jetbrains.atomicfu

import org.objectweb.asm.*
import org.objectweb.asm.ClassReader.SKIP_CODE
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.commons.InstructionAdapter
import java.io.ByteArrayInputStream
import java.io.File

class TypeInfo(val fuType: Type, val pritiveType: Type)

private const val JUCA = "java/util/concurrent/atomic"

private val TYPES_MAP: Map<Type, TypeInfo> =
    listOf("Integer" to Type.INT_TYPE, "Long" to Type.LONG_TYPE, "Reference" to Type.getObjectType("java/lang/Object"))
    .associateBy(
        { (n, _) -> Type.getObjectType("$JUCA/Atomic$n") },
        { (n, p) -> TypeInfo(
            fuType = Type.getObjectType("$JUCA/Atomic${n}FieldUpdater"),
            pritiveType = p)
        })

private operator fun Int.contains(bit: Int) = this and bit != 0
private infix fun Int.wo(bit: Int) = this and bit.inv()

private inline fun code(mv: MethodVisitor, block: InstructionAdapter.() -> Unit) {
    block(InstructionAdapter(mv))
}

data class Field(val className: String, val fieldName: String) {
    override fun toString(): String = "$className::$fieldName"
}

class FieldInfo(field: Field, val type: Type) {
    val className = field.className
    val fieldName = field.fieldName
    val fuName = field.fieldName + '$' + "FU"
    val typeInfo = TYPES_MAP[type]!!
    val fuType = typeInfo.fuType
    val primitiveType = typeInfo.pritiveType
}

class AtomicFUTransformer(private val dir: File) {
    private var hasErrors = false
    private var transformed = false
    private val fields = mutableMapOf<Field, FieldInfo>()

    private fun classFiles() = dir.walk()
        .filter { it.name.endsWith(".class") }

    private fun info(msg: String) {
        println("AtomicFU: $msg")
    }

    private fun error(msg: String) {
        println("AtomicFU ERROR: $msg")
        hasErrors = true
    }

    fun transform() {
        info("Analyzing $dir...")
        classFiles().forEach { collectFields(it) }
        if (hasErrors) throw Exception("Encountered errors while collecting fields")
        info("Transforming $dir...")
        classFiles().forEach { transformFile(it) }
        if (hasErrors) throw Exception("Encountered errors while transforming")
    }

    private fun collectFields(file: File) {
        ClassReader(file.inputStream()).accept(CollectorCV(), SKIP_CODE)
    }

    private fun transformFile(file: File) {
        transformed = false
        val bytes = file.readBytes()
        val cw = ClassWriter(COMPUTE_MAXS or COMPUTE_FRAMES)
        ClassReader(ByteArrayInputStream(bytes)).accept(TransformerCV(cw), SKIP_FRAMES)
        if (transformed) {
            info("Writing transformed $file")
            file.writeBytes(cw.toByteArray())
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
            val type = Type.getType(desc)
            if (type in TYPES_MAP) {
                val field = Field(className, name)
                info("Found field $field : ${type.className}")
                if (ACC_PUBLIC in access) error("Field $field cannot be public")
                if (ACC_FINAL !in access) error("Field $field must be final")
                fields[field] = FieldInfo(field, type)
            }
            return super.visitField(access, name, desc, signature, value)
        }
    }

    private inner class TransformerCV(cv: ClassVisitor) : CV(cv) {
        override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
            val type = Type.getType(desc)
            if (type in TYPES_MAP) {
                val f = fields[Field(className, name)]!!
                check(f.type == type)
                super.visitField((access wo ACC_FINAL) or ACC_VOLATILE, f.fieldName, f.primitiveType.descriptor, null, null)
                super.visitField(access or ACC_STATIC, f.fuName, f.fuType.descriptor, null, null)
                code(super.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null)) {
                    visitCode()
                    visitTypeInsn(Opcodes.NEW, f.fuType.internalName)
                    dup()
                    invokespecial(f.fuType.internalName, "<init>", "()V", false)
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
            return TransformerMV(super.visitMethod(access, name, desc, signature, exceptions))
        }
    }

    private inner class TransformerMV(mv: MethodVisitor?) : MethodVisitor(ASM5, mv) {

    }
}