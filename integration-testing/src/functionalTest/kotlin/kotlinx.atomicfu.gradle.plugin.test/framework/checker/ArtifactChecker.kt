/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.checker

import kotlinx.atomicfu.gradle.plugin.test.framework.runner.GradleBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.build
import org.objectweb.asm.*
import java.io.File
import kotlin.test.assertFalse

internal abstract class ArtifactChecker(val targetDir: File) {

    private val ATOMIC_FU_REF = "Lkotlinx/atomicfu/".toByteArray()
    protected val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"

    protected val projectName = targetDir.name.substringBeforeLast("-")

    val buildDir
        get() = targetDir.resolve("build").also {
            require(it.exists() && it.isDirectory) { "Could not find `build/` directory in the target directory of the project $projectName: ${targetDir.path}" }
        }

    abstract fun checkReferences()

    protected fun ByteArray.findAtomicfuRef(): Boolean {
        loop@for (i in 0 until this.size - ATOMIC_FU_REF.size) {
            for (j in ATOMIC_FU_REF.indices) {
                if (this[i + j] != ATOMIC_FU_REF[j]) continue@loop
            }
            return true
        }
        return false
    }
}

private class BytecodeChecker(targetDir: File) : ArtifactChecker(targetDir) {

    override fun checkReferences() {
        val atomicfuDir = buildDir.resolve("classes/atomicfu/")
        (if (atomicfuDir.exists() && atomicfuDir.isDirectory) atomicfuDir else buildDir).let {
            it.walkBottomUp().filter { it.isFile && it.name.endsWith(".class") }.forEach { clazz ->
                assertFalse(clazz.readBytes().eraseMetadata().findAtomicfuRef(), "Found kotlinx/atomicfu in class file ${clazz.path}")
            }
        }
    }

    // The atomicfu compiler plugin does not remove atomic properties from metadata,
    // so for now we check that there are no ATOMIC_FU_REF left in the class bytecode excluding metadata.
    // This may be reverted after the fix in the compiler plugin transformer (See #254).
    private fun ByteArray.eraseMetadata(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        ClassReader(this).accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                return if (descriptor == KOTLIN_METADATA_DESC) null else super.visitAnnotation(descriptor, visible)
            }
        }, ClassReader.SKIP_FRAMES)
        return cw.toByteArray()
    }
}

internal fun GradleBuild.buildAndCheckBytecode() {
    val buildResult = build()
    require(buildResult.isSuccessful) { "Build of the project $projectName failed:\n ${buildResult.output}" }
    BytecodeChecker(this.targetDir).checkReferences()
}

