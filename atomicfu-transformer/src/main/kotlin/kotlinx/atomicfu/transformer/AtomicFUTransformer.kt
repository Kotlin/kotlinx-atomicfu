/*
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

private val AFU_CLASSES: Map<String, TypeInfo> = mapOf(
    "$AFU_PKG/AtomicInt" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicIntegerFieldUpdater"), INT_TYPE, INT_TYPE),
    "$AFU_PKG/AtomicLong" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicLongFieldUpdater"), LONG_TYPE, LONG_TYPE),
    "$AFU_PKG/AtomicRef" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicReferenceFieldUpdater"), OBJECT_TYPE, OBJECT_TYPE),
    "$AFU_PKG/AtomicBoolean" to TypeInfo(Type.getObjectType("$JUCA_PKG/AtomicIntegerFieldUpdater"), BOOLEAN_TYPE, INT_TYPE)
)

private val WRAPPER: Map<Type, String> = mapOf(
    Type.INT_TYPE to "java/lang/Integer",
    Type.LONG_TYPE to "java/lang/Long",
    Type.BOOLEAN_TYPE to "java/lang/Boolean"
)

private val AFU_TYPES: Map<Type, TypeInfo> = AFU_CLASSES.mapKeys { Type.getObjectType(it.key) }

private val METHOD_HANDLES = "$JLI_PKG/MethodHandles"
private val LOOKUP = "$METHOD_HANDLES\$Lookup"
private val VH_TYPE = Type.getObjectType("$JLI_PKG/VarHandle")

private val STRING_TYPE = Type.getObjectType("java/lang/String")
private val CLASS_TYPE = Type.getObjectType("java/lang/Class")

private fun String.prettyStr() = replace('/', '.')

data class MethodId(val owner: String, val name: String, val desc: String, val invokeOpcode: Int) {
    override fun toString(): String = "${owner.prettyStr()}::$name"
}

private const val AFU_CLS = "$AFU_PKG/AtomicFU"

private val FACTORIES: Set<MethodId> = setOf(
    MethodId(AFU_CLS, ATOMIC, "(Ljava/lang/Object;)L$AFU_PKG/AtomicRef;", INVOKESTATIC),
    MethodId(AFU_CLS, ATOMIC, "(I)L$AFU_PKG/AtomicInt;", INVOKESTATIC),
    MethodId(AFU_CLS, ATOMIC, "(J)L$AFU_PKG/AtomicLong;", INVOKESTATIC),
    MethodId(AFU_CLS, ATOMIC, "(Z)L$AFU_PKG/AtomicBoolean;", INVOKESTATIC)
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

data class FieldId(val owner: String, val name: String) {
    override fun toString(): String = "${owner.prettyStr()}::$name"
}

class FieldInfo(val fieldId: FieldId, val fieldType: Type) {
    val owner = fieldId.owner
    val ownerType: Type = Type.getObjectType(owner)
    val typeInfo = AFU_CLASSES[fieldType.internalName]!!
    val fuType = typeInfo.fuType
    val accessors = mutableSetOf<MethodId>()
    var hasExternalAccess = false
    val name: String
        get() = if (hasExternalAccess) mangleInternal(fieldId.name) else fieldId.name
    val fuName: String
        get() {
            val fuName = fieldId.name + '$' + "FU"
            return if (hasExternalAccess) mangleInternal(fuName) else fuName
        }

    override fun toString(): String = "${owner.prettyStr()}::$name"

    fun getPrimitiveType(vh: Boolean): Type = if (vh) typeInfo.originalType else typeInfo.transformedType
    private fun mangleInternal(fieldName: String): String = "$fieldName\$internal"
}

enum class Variant { FU, VH, BOTH }

class AtomicFUTransformer(
    classpath: List<String>,
    inputDir: File,
    outputDir: File = inputDir,
    var variant: Variant = Variant.FU
) : AtomicFUTransformerBase(inputDir, outputDir) {

    private val classLoader = URLClassLoader(
        (listOf(inputDir) + (classpath.map { File(it) } - outputDir))
            .map { it.toURI().toURL() }.toTypedArray()
    )

    private val fields = mutableMapOf<FieldId, FieldInfo>()
    private val accessors = mutableMapOf<MethodId, FieldInfo>()

    override fun transform() {
        info("Analyzing in $inputDir")
        val succ = analyzeFiles()
        if (hasErrors) throw Exception("Encountered errors while collecting fields")
        if (succ || outputDir == inputDir) {
            // perform transformation
            info("Transforming to $outputDir")
            val vh = variant == Variant.VH
            inputDir.walk().filter { it.isFile }.forEach { file ->
                val bytes = file.readBytes()
                val outBytes = if (file.isClassFile()) transformFile(file, bytes, vh) else bytes
                val outFile = file.toOutputFile()
                outFile.mkdirsAndWrite(outBytes)
                if (variant == Variant.BOTH && outBytes !== bytes) {
                    val vhBytes = transformFile(file, bytes, true)
                    val vhFile = outputDir / "META-INF" / "versions" / "9" / file.relativeTo(inputDir).toString()
                    vhFile.mkdirsAndWrite(vhBytes)
                }
            }
            if (hasErrors) throw Exception("Encountered errors while transforming")
        } else {
            info("Nothing to transform -- all classes are up to date")
        }
    }

    private fun analyzeFiles(): Boolean {
        var inpFilesTime = 0L
        var outFilesTime = 0L
        // 1 phase: visit methods and fields, register all accessors
        inputDir.walk().filter { it.isFile }.forEach { file ->
            inpFilesTime = inpFilesTime.coerceAtLeast(file.lastModified())
            if (file.isClassFile()) analyzeFile(file)
        }
        // 2 phase: visit method bodies for external references to fields
        inputDir.walk().filter { it.isFile }.forEach { file ->
            if (file.isClassFile()) analyzeExternalRefs(file)
            outFilesTime = outFilesTime.coerceAtLeast(file.toOutputFile().lastModified())
        }
        return inpFilesTime > outFilesTime
    }

    private fun analyzeFile(file: File) {
        file.inputStream().use { ClassReader(it).accept(CollectorCV(), SKIP_FRAMES) }
    }

    private fun analyzeExternalRefs(file: File) {
        file.inputStream().use { ClassReader(it).accept(ReferencesCollectorCV(), SKIP_FRAMES) }
    }

    private fun transformFile(file: File, bytes: ByteArray, vh: Boolean): ByteArray {
        transformed = false
        val cw = CW()
        val cv = TransformerCV(cw, vh)
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
                insns[2].isAreturn()
            ) {
                val fi = insns[1] as FieldInsnNode
                val fieldName = fi.name
                val field = FieldId(className, fieldName)
                val fieldType = Type.getType(fi.desc)
                val accessorMethod = MethodId(className, name, desc, accessToInvokeOpcode(access))
                info("$field accessor $name found")
                val fieldInfo = registerField(field, fieldType)
                fieldInfo.accessors += accessorMethod
                accessors[accessorMethod] = fieldInfo
            }
        }
    }

    // returns a type on which this is a potential accessor
    private fun getPotentialAccessorType(access: Int, className: String, methodType: Type): Type? {
        if (methodType.returnType !in AFU_TYPES) return null
        return if (access and ACC_STATIC != 0) {
            if (methodType.argumentTypes.size == 1 && methodType.argumentTypes[0].sort == OBJECT)
                methodType.argumentTypes[0] else null
        } else {
            // if it not static, then it must be final
            if (access and ACC_FINAL != 0 && methodType.argumentTypes.isEmpty())
                Type.getObjectType(className) else null
        }
    }

    private inner class ReferencesCollectorCV : CV(null) {
        override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            // skip accessor method - already registered in the previous phase
            val methodId = MethodId(className, name, desc, accessToInvokeOpcode(access))
            accessors[methodId]?.let { return null }
            return ReferencesCollectorMV(className.ownerPackageName)
        }
    }

    private inner class ReferencesCollectorMV(
        private val packageName: String
    ) : MethodVisitor(ASM5) {
        override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
            val methodId = MethodId(owner, name, desc, opcode)
            // compare owner packages
            if (methodId.owner.ownerPackageName != packageName) {
                accessors[methodId]?.let { it.hasExternalAccess = true }
            }
        }
    }

    private inner class TransformerCV(
        cv: ClassVisitor,
        private val vh: Boolean
    ) : CV(cv) {
        private var source: String? = null
        var sourceInfo: SourceInfo? = null

        private var originalClinit: MethodNode? = null
        private var newClinit: MethodNode? = null

        private fun newClinit() = MethodNode(ASM5, ACC_STATIC, "<clinit>", "()V", null, null)
        fun getOrCreateNewClinit(): MethodNode = newClinit ?: newClinit().also { newClinit = it }

        override fun visitSource(source: String?, debug: String?) {
            this.source = source
            super.visitSource(source, debug)
        }

        override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
            val fieldType = Type.getType(desc)
            if (fieldType.sort == OBJECT && fieldType.internalName in AFU_CLASSES) {
                val fieldId = FieldId(className, name)
                val f = fields[fieldId]!!
                val protection = when {
                    f.hasExternalAccess -> ACC_PUBLIC or ACC_SYNTHETIC
                    f.accessors.isEmpty() -> ACC_PRIVATE
                    else -> 0
                }
                val primitiveType = f.getPrimitiveType(vh)
                val fv = super.visitField(protection or ACC_VOLATILE, f.name, primitiveType.descriptor, null, null)
                if (vh) vhField(protection, f) else fuField(protection, f)
                transformed = true
                return fv
            }
            return super.visitField(access, name, desc, signature, value)
        }

        // Generates static VarHandle field
        private fun vhField(protection: Int, f: FieldInfo) {
            super.visitField(protection or ACC_FINAL or ACC_STATIC, f.fuName, VH_TYPE.descriptor, null, null)
            code(getOrCreateNewClinit()) {
                invokestatic(METHOD_HANDLES, "lookup", "()L$LOOKUP;", false)
                aconst(Type.getObjectType(className))
                aconst(f.name)
                val primitiveType = f.getPrimitiveType(vh)
                if (primitiveType.sort == OBJECT) {
                    aconst(primitiveType)
                } else {
                    val wrapper = WRAPPER[primitiveType]!!
                    getstatic(wrapper, "TYPE", CLASS_TYPE.descriptor)
                }
                invokevirtual(
                    LOOKUP, "findVarHandle",
                    getMethodDescriptor(VH_TYPE, CLASS_TYPE, STRING_TYPE, CLASS_TYPE), false
                )
                putstatic(className, f.fuName, VH_TYPE.descriptor)
            }
        }

        // Generates static AtomicXXXFieldUpdater field
        private fun fuField(protection: Int, f: FieldInfo) {
            super.visitField(protection or ACC_FINAL or ACC_STATIC, f.fuName, f.fuType.descriptor, null, null)
            code(getOrCreateNewClinit()) {
                val params = mutableListOf<Type>()
                params += CLASS_TYPE
                aconst(Type.getObjectType(className))
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

        override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            val method = MethodId(className, name, desc, accessToInvokeOpcode(access))
            if (method in accessors) {
                return null // drop accessor
            }
            val sourceInfo = SourceInfo(method, source)
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
            val mv = TransformerMV(sourceInfo, access, name, desc, signature, exceptions, superMV, vh)
            this.sourceInfo = mv.sourceInfo
            return mv
        }

        override fun visitEnd() {
            // collect class initialization
            if (originalClinit != null || newClinit != null) {
                val newClinit = newClinit
                if (newClinit == null) {
                    // dump just original clinit
                    originalClinit!!.accept(cv)
                } else {
                    // create dummy base code if needed
                    val originalClinit = originalClinit ?: newClinit().also {
                        code(it) { visitInsn(Opcodes.RETURN) }
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
        mv: MethodVisitor,
        private val vh: Boolean
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
                val primitiveType = f.getPrimitiveType(vh)
                val j = FieldInsnNode(
                    if (iv.name == "getValue") GETFIELD else PUTFIELD,
                    f.owner, f.name, primitiveType.descriptor
                )
                instructions.set(iv, j) // replace invokevirtual with get/setfield
                return j.next
            }
            // update method invocation
            if (vh) vhOperation(iv, typeInfo) else fuOperation(iv, typeInfo)
            // insert swap after field load
            val swap = InsnNode(SWAP)
            instructions.insert(ld, swap)
            return swap.next
        }

        private fun vhOperation(iv: MethodInsnNode, typeInfo: TypeInfo) {
            val methodType = Type.getMethodType(iv.desc)
            val args = methodType.argumentTypes
            iv.owner = VH_TYPE.internalName
            val params = mutableListOf<Type>(OBJECT_TYPE, *args)
            val long = typeInfo.originalType == LONG_TYPE
            when (iv.name) {
                "lazySet" -> iv.name = "setRelease"
                "getAndIncrement" -> {
                    instructions.insertBefore(iv, insns { if (long) lconst(1) else iconst(1) })
                    params += typeInfo.originalType
                    iv.name = "getAndAdd"
                }
                "getAndDecrement" -> {
                    instructions.insertBefore(iv, insns { if (long) lconst(-1) else iconst(-1) })
                    params += typeInfo.originalType
                    iv.name = "getAndAdd"
                }
                "addAndGet" -> {
                    bumpLocals(if (long) 2 else 1)
                    instructions.insertBefore(iv, insns {
                        if (long) dup2() else dup()
                        store(tempLocal, typeInfo.originalType)
                    })
                    iv.name = "getAndAdd"
                    instructions.insert(iv, insns {
                        load(tempLocal, typeInfo.originalType)
                        add(typeInfo.originalType)
                    })
                }
                "incrementAndGet" -> {
                    instructions.insertBefore(iv, insns { if (long) lconst(1) else iconst(1) })
                    params += typeInfo.originalType
                    iv.name = "getAndAdd"
                    instructions.insert(iv, insns {
                        if (long) lconst(1) else iconst(1)
                        add(typeInfo.originalType)
                    })
                }
                "decrementAndGet" -> {
                    instructions.insertBefore(iv, insns { if (long) lconst(-1) else iconst(-1) })
                    params += typeInfo.originalType
                    iv.name = "getAndAdd"
                    instructions.insert(iv, insns {
                        if (long) lconst(-1) else iconst(-1)
                        add(typeInfo.originalType)
                    })
                }
            }
            iv.desc = getMethodDescriptor(methodType.returnType, *params.toTypedArray())
        }

        private fun fuOperation(iv: MethodInsnNode, typeInfo: TypeInfo) {
            val methodType = Type.getMethodType(iv.desc)
            val trans = typeInfo.originalType != typeInfo.transformedType
            val args = methodType.argumentTypes
            var ret = methodType.returnType
            if (trans) {
                args.forEachIndexed { i, type -> if (type == typeInfo.originalType) args[i] = typeInfo.transformedType }
                if (iv.name == "getAndSet") ret = typeInfo.transformedType
            }
            iv.owner = typeInfo.fuType.internalName
            iv.desc = getMethodDescriptor(ret, OBJECT_TYPE, *args)
        }

        private fun fixupLoadedAtomicVar(f: FieldInfo, ld: FieldInsnNode): AbstractInsnNode? {
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
                    val lv = localVar(v)
                    if (lv != null) {
                        // Stored to a local variable with an entry in LVT (typically because of inline function)
                        if (lv.desc != f.fieldType.descriptor)
                            abort("field $f was stored to a local variable #$v \"${lv.name}\" with unexpected type: ${lv.desc}")
                        // correct local variable descriptor
                        lv.desc = f.ownerType.descriptor
                        lv.signature = null
                        // process all loads of this variable in the corresponding local variable range
                        forVarLoads(v, lv.start, lv.end) { otherLd ->
                            fixupVarLoad(f, ld, otherLd)
                        }
                    } else {
                        // Spilled temporarily to a local variable w/o an entry in LVT -> fixup only one load
                        fixupVarLoad(f, ld, nextVarLoad(v, next))
                    }
                    return next
                }
                else -> abort("cannot happen")
            }
        }

        private fun fixupVarLoad(f: FieldInfo, ld: FieldInsnNode, otherLd: VarInsnNode): AbstractInsnNode? {
            val ldCopy = ld.clone(null) as FieldInsnNode
            instructions.insert(otherLd, ldCopy)
            return fixupLoadedAtomicVar(f, ldCopy)
        }

        private fun transform(i: AbstractInsnNode): AbstractInsnNode? {
            when (i) {
                is MethodInsnNode -> {
                    val methodId = MethodId(i.owner, i.name, i.desc, i.opcode)
                    when {
                        methodId in FACTORIES -> {
                            if (name != "<init>") abort("factory $methodId is used outside of constructor")
                            val next = i.nextUseful
                            val fieldId = (next as? FieldInsnNode)?.checkPutField()
                                ?: abort("factory $methodId invocation must be followed by putfield")
                            instructions.remove(i)
                            transformed = true
                            val f = fields[fieldId]!!
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
                            instructions.insert(i, j)
                            instructions.remove(i)
                            transformed = true
                            return fixupLoadedAtomicVar(f, j)
                        }
                        i.opcode == INVOKEVIRTUAL && i.owner in AFU_CLASSES -> {
                            abort("standalone invocation of $methodId that was not traced to previous field load", i)
                        }
                    }
                }
                is FieldInsnNode -> {
                    val fieldId = FieldId(i.owner, i.name)
                    if (i.opcode == GETFIELD && fieldId in fields) {
                        // Convert GETFIELD to GETSTATIC on var handle / field updater
                        val f = fields[fieldId]!!
                        if (i.desc != f.fieldType.descriptor) return i.next // already converted get/setfield
                        i.opcode = GETSTATIC
                        i.name = f.fuName
                        i.desc = if (vh) VH_TYPE.descriptor else f.fuType.descriptor
                        transformed = true
                        return fixupLoadedAtomicVar(f, i)
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