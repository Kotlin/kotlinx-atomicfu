/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.transformer

import kotlinx.metadata.*
import kotlinx.metadata.jvm.*
import org.objectweb.asm.tree.*

const val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"

class MetadataTransformer(
    removeFields: Set<FieldId>,
    removeMethods: Set<MethodId>
) {
    private val removeFieldSignatures: List<JvmFieldSignature> =
        removeFields.map { JvmFieldSignature(it.name, it.desc) }
    private val removeMethodSignatures: List<JvmMethodSignature> =
        removeMethods.map { JvmMethodSignature(it.name, it.desc) }
    private var transformed = false

    @Suppress("UNCHECKED_CAST")
    fun transformMetadata(metadataAnnotation: AnnotationNode): Boolean {
        val map = metadataAnnotation.asMap()
        val hdr = KotlinClassHeader(
            kind = map["k"] as Int?,
            metadataVersion = (map["mv"] as? List<Int>)?.toIntArray(),
            bytecodeVersion = (map["bv"] as? List<Int>)?.toIntArray(),
            data1 = (map["d1"] as? List<String>)?.toTypedArray(),
            data2 = (map["d2"] as? List<String>)?.toTypedArray(),
            extraString = map["xs"] as String?,
            packageName = map["pn"] as String?,
            extraInt = map["xi"] as Int?
        )
        val result = when (val metadata = KotlinClassMetadata.read(hdr)) {
            is KotlinClassMetadata.Class -> {
                val w = KotlinClassMetadata.Class.Writer()
                metadata.accept(ClassFilter(w))
                w.write(hdr.metadataVersion, hdr.bytecodeVersion, hdr.extraInt)
            }
            is KotlinClassMetadata.FileFacade -> {
                val w = KotlinClassMetadata.FileFacade.Writer()
                metadata.accept(PackageFilter(w))
                w.write(hdr.metadataVersion, hdr.bytecodeVersion, hdr.extraInt)
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                val w = KotlinClassMetadata.MultiFileClassPart.Writer()
                metadata.accept(PackageFilter(w))
                w.write(metadata.facadeClassName, hdr.metadataVersion, hdr.bytecodeVersion, hdr.extraInt)
            }
            else -> return false // not transformed
        }
        if (!transformed) return false
        result.apply {
            with (metadataAnnotation) {
                // read resulting header & update annotation data
                setKey("d1", header.data1.toList())
                setKey("d2", header.data2.toList())
            }
        }
        return true // transformed
    }

    private inner class ClassFilter(v: KmClassVisitor?) : KmClassVisitor(v) {
        private val supertypes = mutableListOf<KmType>()

        override fun visitSupertype(flags: Flags): KmTypeVisitor? =
            KmType(flags).also { supertypes += it }

        override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
            PropertyFilter(KmProperty(flags, name, getterFlags, setterFlags), super.visitProperty(flags, name, getterFlags, setterFlags)!!)
        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor =
            FunctionFilter(KmFunction(flags, name), super.visitFunction(flags, name)!!)

        override fun visitEnd() {
            // Skip supertype if it is SynchronizedObject (it is an alias to Any)
            supertypes.forEach { type ->
                if (type.abbreviatedType?.classifier == SynchronizedObjectAlias) {
                    transformed = true
                } else
                    type.accept(super.visitSupertype(type.flags)!!)
            }
            super.visitEnd()
        }
    }

    private inner class PackageFilter(v: KmPackageVisitor?) : KmPackageVisitor(v) {
        override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
            PropertyFilter(KmProperty(flags, name, getterFlags, setterFlags), super.visitProperty(flags, name, getterFlags, setterFlags)!!)
        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor =
            FunctionFilter(KmFunction(flags, name), super.visitFunction(flags, name)!!)
    }

    private class PropertyExtensionNode : JvmPropertyExtensionVisitor() {
        private var jvmFlags: Flags? = null
        var fieldSignature: JvmFieldSignature? = null
        private var getterSignature: JvmMethodSignature? = null
        private var setterSignature: JvmMethodSignature? = null
        private var syntheticMethodForAnnotationsDesc: JvmMethodSignature? = null

        override fun visit(
            jvmFlags: Flags,
            fieldSignature: JvmFieldSignature?,
            getterSignature: JvmMethodSignature?,
            setterSignature: JvmMethodSignature?
        ) {
            check(this.jvmFlags == null)
            this.jvmFlags = jvmFlags
            this.fieldSignature = fieldSignature
            this.getterSignature = getterSignature
            this.setterSignature = setterSignature
        }

        override fun visitSyntheticMethodForAnnotations(signature: JvmMethodSignature?) {
            check(syntheticMethodForAnnotationsDesc == null)
            this.syntheticMethodForAnnotationsDesc = signature
        }

        fun accept(v : JvmPropertyExtensionVisitor) {
            if (jvmFlags != null) {
                v.visit(jvmFlags!!, fieldSignature, getterSignature, setterSignature)
            }
            syntheticMethodForAnnotationsDesc?.let { v.visitSyntheticMethodForAnnotations(it) }
            v.visitEnd()
        }
    }

    private inner class PropertyFilter(
        private val delegate: KmProperty,
        private val v: KmPropertyVisitor
    ) : KmPropertyVisitor(delegate) {
        private var extension: PropertyExtensionNode? = null

        override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
            check(type == JvmPropertyExtensionVisitor.TYPE)
            check(extension == null)
            return PropertyExtensionNode().also { extension = it }
        }

        override fun visitEnd() {
            if (extension?.fieldSignature in removeFieldSignatures) {
                // remove this function
                transformed = true
                return
            }
            delegate.receiverParameterType?.fixType { delegate.receiverParameterType = it }
            delegate.returnType.fixType { delegate.returnType = it }
            // keeping this property
            extension?.accept(delegate.visitExtensions(JvmPropertyExtensionVisitor.TYPE) as JvmPropertyExtensionVisitor)
            delegate.accept(v)
        }
    }

    private class FunctionExtensionNode : JvmFunctionExtensionVisitor() {
        var signature: JvmMethodSignature? = null
        private var originalInternalName: String? = null

        override fun visit(signature: JvmMethodSignature?) {
            check(this.signature == null)
            this.signature = signature
        }

        override fun visitLambdaClassOriginName(internalName: String) {
            check(originalInternalName == null)
            originalInternalName = internalName
        }

        fun accept(v : JvmFunctionExtensionVisitor) {
            signature?.let { v.visit(it) }
            originalInternalName?.let { v.visitLambdaClassOriginName(it) }
            v.visitEnd()
        }
    }

    private inner class FunctionFilter(
        private val delegate: KmFunction,
        private val v: KmFunctionVisitor
    ) : KmFunctionVisitor(delegate) {
        private var extension: FunctionExtensionNode? = null

        override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? {
            check(type == JvmFunctionExtensionVisitor.TYPE)
            check(extension == null)
            return FunctionExtensionNode().also { extension = it }
        }

        override fun visitEnd() {
            if (extension?.signature in removeMethodSignatures) {
                // remove this function
                transformed = true
                return
            }
            // keeping this function
            extension?.accept(delegate.visitExtensions(JvmFunctionExtensionVisitor.TYPE) as JvmFunctionExtensionVisitor)
            delegate.accept(v)
        }
    }

    private fun KmType.fixType(update: (KmType) -> Unit) {
        if (this.abbreviatedType?.classifier == ReentrantLockAlias) {
            update(ReentrantLockType)
            transformed = true
        }
    }
}

private val SynchronizedObjectAlias = KmClassifier.TypeAlias("kotlinx/atomicfu/locks/SynchronizedObject")

private val ReentrantLockAlias = KmClassifier.TypeAlias("kotlinx/atomicfu/locks/ReentrantLock")
private val ReentrantLockType = KmType(0).apply {
    classifier = KmClassifier.Class("java/util/concurrent/locks/ReentrantLock")        
}

@Suppress("UNCHECKED_CAST")
private fun AnnotationNode.asMap(): Map<String, Any?> {
    val result = HashMap<String, Any?>()
    for (i in 0 until values.size step 2) {
        result.put(values[i] as String, values[i + 1])
    }
    return result
}

private fun AnnotationNode.setKey(key: String, value: Any?) {
    for (i in 0 until values.size step 2) {
        if (values[i] == key) {
            values[i + 1] = value
            return
        }
    }
    error("Annotation key '$key' is not found")
}