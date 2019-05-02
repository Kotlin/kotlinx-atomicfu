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
                metadata.accept(object : KmClassVisitor(w) {
                    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                        PropertyFilterNode(super.visitProperty(flags, name, getterFlags, setterFlags)!!)
                    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor =
                        FunctionFilterNode(super.visitFunction(flags, name)!!)
                })
                w.write(hdr.metadataVersion, hdr.bytecodeVersion, hdr.extraInt)
            }
            is KotlinClassMetadata.FileFacade -> {
                val w = KotlinClassMetadata.FileFacade.Writer()
                metadata.accept(object : KmPackageVisitor(w) {
                    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                        PropertyFilterNode(super.visitProperty(flags, name, getterFlags, setterFlags)!!)
                    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor =
                        FunctionFilterNode(super.visitFunction(flags, name)!!)
                })
                w.write(hdr.metadataVersion, hdr.bytecodeVersion, hdr.extraInt)
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                val w = KotlinClassMetadata.MultiFileClassPart.Writer()
                metadata.accept(object : KmPackageVisitor(w) {
                    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                        PropertyFilterNode(super.visitProperty(flags, name, getterFlags, setterFlags)!!)
                    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor =
                        FunctionFilterNode(super.visitFunction(flags, name)!!)
                })
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

    private interface VersionRequirementMember {
        fun accept(v: KmVersionRequirementVisitor)
    }

    private class Version1(
        val kind: KmVersionRequirementVersionKind,
        val level: KmVersionRequirementLevel,
        val errorCode: Int?,
        val message: String?
    ) : VersionRequirementMember {
        override fun accept(v: KmVersionRequirementVisitor) {
            v.visit(kind, level, errorCode, message)
        }
    }

    private class Version2(
        val major: Int,
        val minor: Int,
        val patch: Int
    ) : VersionRequirementMember {
        override fun accept(v: KmVersionRequirementVisitor) {
            v.visitVersion(major, minor, patch)
        }
    }

    private class VersionRequirementNode : KmVersionRequirementVisitor() {
        private val m = ArrayList<VersionRequirementMember>()

        override fun visit(
            kind: KmVersionRequirementVersionKind,
            level: KmVersionRequirementLevel,
            errorCode: Int?,
            message: String?
        ) {
            m += Version1(kind, level, errorCode, message)
        }

        override fun visitVersion(major: Int, minor: Int, patch: Int) {
            m += Version2(major, minor, patch)
        }

        fun accept(v: KmVersionRequirementVisitor) {
            m.forEach { it.accept(v) }
            v.visitEnd()
        }
    }

    private class TypeParameterExtensionNode : JvmTypeParameterExtensionVisitor() {
        private val annotations = ArrayList<KmAnnotation>()

        override fun visitAnnotation(annotation: KmAnnotation) { annotations += annotation }

        fun accept(v: JvmTypeParameterExtensionVisitor) {
            annotations.forEach { v.visitAnnotation(it) }
            v.visitEnd()
        }
    }

    private class TypeParameterNode(
        val flags: Flags,
        val name: String,
        val id: Int,
        val variance: KmVariance
    ) : KmTypeParameterVisitor() {
        private val upperBounds = ArrayList<FlagsTypeNode>()
        private var extension: TypeParameterExtensionNode? = null

        override fun visitUpperBound(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { upperBounds += it }

        override fun visitExtensions(type: KmExtensionType): KmTypeParameterExtensionVisitor? {
            check(type == JvmTypeParameterExtensionVisitor.TYPE)
            check(extension == null)
            return TypeParameterExtensionNode().also { extension = it }
        }

        fun accept(v: KmTypeParameterVisitor) {
            upperBounds.forEach { it.accept(v.visitUpperBound(it.flags)!!) }
            extension?.accept(v.visitExtensions(JvmTypeParameterExtensionVisitor.TYPE) as JvmTypeParameterExtensionVisitor)
            v.visitEnd()
        }
    }

    private class ValueParameterNode(
        val flags: Flags,
        val name: String
    ) : KmValueParameterVisitor() {
        private val types = ArrayList<FlagsTypeNode>()
        private val varargs = ArrayList<FlagsTypeNode>()

        override fun visitType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { types += it }

        override fun visitVarargElementType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { varargs += it }

        fun accept(v: KmValueParameterVisitor) {
            types.forEach { it.accept(v.visitType(it.flags)!!) }
            varargs.forEach { it.accept(v.visitVarargElementType(it.flags)!!) }
            v.visitEnd()
        }
    }

    private class TypeExtensionNode : JvmTypeExtensionVisitor() {
        private var isRaw: Boolean? = null
        private val annotations = ArrayList<KmAnnotation>()

        override fun visit(isRaw: Boolean) { this.isRaw = isRaw }
        override fun visitAnnotation(annotation: KmAnnotation) { annotations += annotation }

        fun accept(v: JvmTypeExtensionVisitor) {
            isRaw?.let { v.visit(it) }
            annotations.forEach { v.visitAnnotation(it) }
            v.visitEnd()
        }
    }

    private class FlagsTypeNode(
        val flags: Flags
    ) : TypeNode()

    private class FlagsVarianceTypeNode(
        val flags: Flags,
        val variance: KmVariance
    ) : TypeNode()

    private class FlagsStringTypeNode(
        val flags: Flags,
        val string: String?
    ) : TypeNode()

    private open class TypeNode : KmTypeVisitor() {
        private val classNames = ArrayList<ClassName>()
        private val typeAliases = ArrayList<ClassName>()
        private val typeParameters = ArrayList<Int>()
        private val arguments = ArrayList<FlagsVarianceTypeNode>()
        private var startProjection = false
        private val abbreviatedTypes = ArrayList<FlagsTypeNode>()
        private val outerTypes = ArrayList<FlagsTypeNode>()
        private val flexibleTypeUpperBounds = ArrayList<FlagsStringTypeNode>()
        private var extension: TypeExtensionNode? = null

        override fun visitClass(name: ClassName) {
            classNames += name
        }

        override fun visitTypeAlias(name: ClassName) {
            typeAliases += name
        }

        override fun visitTypeParameter(id: Int) {
            typeParameters += id
        }

        override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? =
            FlagsVarianceTypeNode(flags, variance).also { arguments += it }

        override fun visitStarProjection() {
            startProjection = true
        }

        override fun visitAbbreviatedType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { abbreviatedTypes += it }

        override fun visitOuterType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { outerTypes += it }

        override fun visitFlexibleTypeUpperBound(flags: Flags, typeFlexibilityId: String?): KmTypeVisitor? =
            FlagsStringTypeNode(flags, typeFlexibilityId).also { flexibleTypeUpperBounds += it }

        override fun visitExtensions(type: KmExtensionType): KmTypeExtensionVisitor? {
            check(type == JvmTypeExtensionVisitor.TYPE)
            check(extension == null)
            return TypeExtensionNode().also { extension = it }
        }

        fun accept(v: KmTypeVisitor) {
            classNames.forEach { v.visitClass(it) }
            typeAliases.forEach { v.visitTypeAlias(it) }
            typeParameters.forEach { v.visitTypeParameter(it) }
            arguments.forEach { it.accept(v.visitArgument(it.flags, it.variance)!!) }
            if (startProjection) v.visitStarProjection()
            abbreviatedTypes.forEach { it.accept(v.visitAbbreviatedType(it.flags)!!) }
            outerTypes.forEach { it.accept(v.visitOuterType(it.flags)!!) }
            flexibleTypeUpperBounds.forEach { it.accept(v.visitFlexibleTypeUpperBound(it.flags, it.string)!!) }
            extension?.accept(v.visitExtensions(JvmTypeExtensionVisitor.TYPE) as JvmTypeExtensionVisitor)
            v.visitEnd()
        }
    }

    private class FlagsInt(
        val flags: Flags,
        val int: Int?
    )

    private class EffectExpressionNode : KmEffectExpressionVisitor() {
        private val data = ArrayList<FlagsInt>()
        private val andArgs = ArrayList<EffectExpressionNode>()
        private val orArgs = ArrayList<EffectExpressionNode>()
        private val consts = ArrayList<Any?>()
        private val types = ArrayList<FlagsTypeNode>()

        override fun visit(flags: Flags, parameterIndex: Int?) {
            data += FlagsInt(flags, parameterIndex)
        }

        override fun visitAndArgument(): KmEffectExpressionVisitor? =
            EffectExpressionNode().also { andArgs += it }

        override fun visitOrArgument(): KmEffectExpressionVisitor? =
            EffectExpressionNode().also { orArgs += it }

        override fun visitConstantValue(value: Any?) {
            consts += value
        }

        override fun visitIsInstanceType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { types += it }

        fun accept(v: KmEffectExpressionVisitor) {
            data.forEach { v.visit(it.flags, it.int) }
            andArgs.forEach { it.accept(v.visitAndArgument()!!) }
            orArgs.forEach { it.accept(v.visitOrArgument()!!) }
            consts.forEach { v.visitConstantValue(it) }
            types.forEach { it.accept(v.visitIsInstanceType(it.flags)!!) }
            v.visitEnd()
        }
    }

    private class EffectNode(
        val type: KmEffectType,
        val invocationKind: KmEffectInvocationKind?
    ) : KmEffectVisitor() {
        private val conclusionOfConditionalEffects = ArrayList<EffectExpressionNode>()
        private val constructorArguments = ArrayList<EffectExpressionNode>()

        override fun visitConclusionOfConditionalEffect(): KmEffectExpressionVisitor? =
            EffectExpressionNode().also { conclusionOfConditionalEffects += it }

        override fun visitConstructorArgument(): KmEffectExpressionVisitor? =
            EffectExpressionNode().also { constructorArguments += it }

        fun accept(v: KmEffectVisitor) {
            conclusionOfConditionalEffects.forEach { it.accept(v.visitConclusionOfConditionalEffect()!!) }
            constructorArguments.forEach { it.accept(v.visitConstructorArgument()!!) }
            v.visitEnd()
        }
    }

    private class ContractNode : KmContractVisitor() {
        private val effects = ArrayList<EffectNode>()

        override fun visitEffect(type: KmEffectType, invocationKind: KmEffectInvocationKind?): KmEffectVisitor? =
            EffectNode(type, invocationKind).also { effects += it }

        fun accept(v: KmContractVisitor) {
            effects.forEach { it.accept(v.visitEffect(it.type, it.invocationKind)!!) }
            v.visitEnd()
        }
    }

    private class PropertyExtensionNode : JvmPropertyExtensionVisitor() {
        var fieldDesc: JvmFieldSignature? = null
        private var getterDesc: JvmMethodSignature? = null
        private var setterDesc: JvmMethodSignature? = null
        private var syntheticMethodForAnnotationsDesc: JvmMethodSignature? = null

        override fun visit(
            fieldDesc: JvmFieldSignature?,
            getterDesc: JvmMethodSignature?,
            setterDesc: JvmMethodSignature?
        ) {
            check(this.fieldDesc == null && this.getterDesc == null && this.setterDesc == null)
            this.fieldDesc = fieldDesc
            this.getterDesc = getterDesc
            this.setterDesc = setterDesc
        }

        override fun visitSyntheticMethodForAnnotations(desc: JvmMethodSignature?) {
            check(syntheticMethodForAnnotationsDesc == null)
            this.syntheticMethodForAnnotationsDesc = desc
        }

        fun accept(v : JvmPropertyExtensionVisitor) {
            if (fieldDesc != null || getterDesc != null || setterDesc != null) {
                v.visit(fieldDesc, getterDesc, setterDesc)
            }
            syntheticMethodForAnnotationsDesc?.let { v.visitSyntheticMethodForAnnotations(it) }
            v.visitEnd()
        }
    }

    private class FunctionExtensionNode : JvmFunctionExtensionVisitor() {
        var desc: JvmMethodSignature? = null
        private var originalInternalName: String? = null

        override fun visit(desc: JvmMethodSignature?) {
            check(this.desc == null)
            this.desc = desc
        }

        override fun visitLambdaClassOriginName(internalName: String) {
            check(originalInternalName == null)
            originalInternalName = internalName
        }

        fun accept(v : JvmFunctionExtensionVisitor) {
            desc?.let { v.visit(it) }
            originalInternalName?.let { v.visitLambdaClassOriginName(it) }
            v.visitEnd()
        }
    }

    private inner class PropertyFilterNode(
        private val v: KmPropertyVisitor
    ) : KmPropertyVisitor() {
        private val typeParameters = ArrayList<TypeParameterNode>()
        private val receiverParameterTypes = ArrayList<FlagsTypeNode>()
        private val returnTypes = ArrayList<FlagsTypeNode>()
        private val valueParameters = ArrayList<ValueParameterNode>()
        private val versionRequirements = ArrayList<VersionRequirementNode>()
        private var extension: PropertyExtensionNode? = null

        override fun visitTypeParameter(
            flags: Flags, name: String, id: Int, variance: KmVariance
        ): KmTypeParameterVisitor? =
            TypeParameterNode(flags, name, id, variance).also { typeParameters += it }

        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { receiverParameterTypes += it }

        override fun visitReturnType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { returnTypes += it }

        override fun visitSetterParameter(flags: Flags, name: String): KmValueParameterVisitor? =
            ValueParameterNode(flags, name).also { valueParameters += it }

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? =
            VersionRequirementNode().also { versionRequirements += it }

        override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
            check(type == JvmPropertyExtensionVisitor.TYPE)
            check(extension == null)
            return PropertyExtensionNode().also { extension = it }
        }

        override fun visitEnd() {
            if (extension?.fieldDesc in removeFieldSignatures) {
                // remove this function
                transformed = true
                return
            }
            // keeping this function
            typeParameters.forEach { it.accept(v.visitTypeParameter(it.flags, it.name, it.id, it.variance)!!) }
            receiverParameterTypes.forEach { it.accept(v.visitReceiverParameterType(it.flags)!!) }
            returnTypes.forEach { it.accept(v.visitReturnType(it.flags)!!) }
            valueParameters.forEach { it.accept(v.visitSetterParameter(it.flags, it.name)!!) }
            versionRequirements.forEach { it.accept(v.visitVersionRequirement()!!) }
            extension?.accept(v.visitExtensions(JvmPropertyExtensionVisitor.TYPE) as JvmPropertyExtensionVisitor)
            v.visitEnd()
        }
    }

    private inner class FunctionFilterNode(
        private val v: KmFunctionVisitor
    ) : KmFunctionVisitor() {
        private val typeParameters = ArrayList<TypeParameterNode>()
        private val receiverParameterTypes = ArrayList<FlagsTypeNode>()
        private val valueParameters = ArrayList<ValueParameterNode>()
        private val returnTypes = ArrayList<FlagsTypeNode>()
        private val contracts = ArrayList<ContractNode>()
        private val versionRequirements = ArrayList<VersionRequirementNode>()
        private var extension: FunctionExtensionNode? = null

        override fun visitTypeParameter(
            flags: Flags, name: String, id: Int, variance: KmVariance
        ): KmTypeParameterVisitor? =
            TypeParameterNode(flags, name, id, variance).also { typeParameters += it }

        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { receiverParameterTypes += it }

        override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? =
            ValueParameterNode(flags, name).also { valueParameters += it }

        override fun visitReturnType(flags: Flags): KmTypeVisitor? =
            FlagsTypeNode(flags).also { returnTypes += it }

        override fun visitContract(): KmContractVisitor? =
            ContractNode().also { contracts += it }

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? =
            VersionRequirementNode().also { versionRequirements += it }

        override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? {
            check(type == JvmFunctionExtensionVisitor.TYPE)
            check(extension == null)
            return FunctionExtensionNode().also { extension = it }
        }

        override fun visitEnd() {
            if (extension?.desc in removeMethodSignatures) {
                // remove this function
                transformed = true
                return
            }
            // keeping this function
            typeParameters.forEach { it.accept(v.visitTypeParameter(it.flags, it.name, it.id, it.variance)!!) }
            receiverParameterTypes.forEach { it.accept(v.visitReceiverParameterType(it.flags)!!) }
            valueParameters.forEach { it.accept(v.visitValueParameter(it.flags, it.name)!!) }
            returnTypes.forEach { it.accept(v.visitReturnType(it.flags)!!) }
            contracts.forEach { it.accept(v.visitContract()!!) }
            versionRequirements.forEach { it.accept(v.visitVersionRequirement()!!) }
            extension?.accept(v.visitExtensions(JvmFunctionExtensionVisitor.TYPE) as JvmFunctionExtensionVisitor)
            v.visitEnd()
        }
    }
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