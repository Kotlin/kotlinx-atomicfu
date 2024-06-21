/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.transformer

import kotlin.metadata.*
import kotlin.metadata.jvm.*
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

    @Suppress("UNCHECKED_CAST")
    fun transformMetadata(metadataAnnotation: AnnotationNode): Boolean {
        val map = metadataAnnotation.asMap()
        val metadata = Metadata(
            kind = map["k"] as Int?,
            metadataVersion = (map["mv"] as? List<Int>)?.toIntArray(),
            data1 = (map["d1"] as? List<String>)?.toTypedArray(),
            data2 = (map["d2"] as? List<String>)?.toTypedArray(),
            extraString = map["xs"] as String?,
            packageName = map["pn"] as String?,
            extraInt = map["xi"] as Int?
        )
        val metadataVersion = JvmMetadataVersion(metadata.metadataVersion[0], metadata.metadataVersion[1], metadata.metadataVersion[2])
        val transformedMetadata = when (val kotlinClassMetadata = KotlinClassMetadata.readStrict(metadata)) {
            is KotlinClassMetadata.Class -> {
                val kmClass = kotlinClassMetadata.kmClass
                KotlinClassMetadata.Class(kmClass.transformKmClass(), metadataVersion, metadata.extraInt).write()
            }
            is KotlinClassMetadata.FileFacade -> {
                val kmPackage = kotlinClassMetadata.kmPackage
                KotlinClassMetadata.FileFacade(kmPackage.removeAtomicfuDeclarations() as KmPackage, metadataVersion, metadata.extraInt).write()
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                val kmPackage = kotlinClassMetadata.kmPackage
                KotlinClassMetadata.MultiFileClassPart(kmPackage.removeAtomicfuDeclarations() as KmPackage, metadata.extraString, metadataVersion, metadata.extraInt).write()
            }
            else -> return false // not transformed
        }
        with (metadataAnnotation) {
            // read the resulting header & update annotation data
            setKey("d1", transformedMetadata.data1.toList())
            setKey("d2", transformedMetadata.data2.toList())
        }
        return true // transformed
    }

    private fun KmClass.transformKmClass() =
        apply {
            supertypes.replaceAll {
                    type ->
                if (type.abbreviatedType?.classifier == SynchronizedObjectAlias) {
                    KmType().apply {
                        classifier = KmClassifier.Class("kotlin/Any")
                    }
                } else {
                    type
                }
            }
            removeAtomicfuDeclarations()
        }

    private fun <T: KmDeclarationContainer> T.removeAtomicfuDeclarations(): T 
        apply {
            functions.removeIf { it.signature in removeMethodSignatures }
            properties.removeIf { it.fieldSignature in removeFieldSignatures }
            properties.forEach { property ->
                property.apply {
                    receiverParameterType?.fixType { property.receiverParameterType = it }
                    returnType.fixType { property.returnType = it }
                }
            }
        }

    private fun KmType.fixType(update: (KmType) -> Unit) {
        if (this.abbreviatedType?.classifier == ReentrantLockAlias) {
            update(ReentrantLockType)
        }
    }
}

private val SynchronizedObjectAlias = KmClassifier.TypeAlias("kotlinx/atomicfu/locks/SynchronizedObject")

private val ReentrantLockAlias = KmClassifier.TypeAlias("kotlinx/atomicfu/locks/ReentrantLock")
private val ReentrantLockType = KmType().apply {
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
