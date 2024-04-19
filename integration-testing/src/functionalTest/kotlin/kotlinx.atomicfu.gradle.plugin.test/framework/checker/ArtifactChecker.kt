/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.checker

import kotlinx.atomicfu.gradle.plugin.test.framework.runner.GradleBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.cleanAndBuild
import org.objectweb.asm.*
import java.io.File
import java.net.URLClassLoader
import kotlin.test.assertFalse

internal abstract class ArtifactChecker(private val targetDir: File) {

    private val ATOMIC_FU_REF = "Lkotlinx/atomicfu/".toByteArray()
    protected val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"

    protected val projectName = targetDir.name.substringBeforeLast("-")

    fun checkClassesInBuildDirectories() {
        targetDir.walkTopDown().filter { it.isDirectory && it.name == "build" }.forEach {
            checkReferences(it)
        }
    }

    protected abstract fun checkReferences(buildDir: File)

    protected fun ByteArray.findAtomicfuRef(): Boolean {
        loop@for (i in 0 .. this.size - ATOMIC_FU_REF.size) {
            for (j in ATOMIC_FU_REF.indices) {
                if (this[i + j] != ATOMIC_FU_REF[j]) continue@loop
            }
            return true
        }
        return false
    }
}

private class BytecodeChecker(private val gradleBuild: GradleBuild) : ArtifactChecker(gradleBuild.targetDir) {

    override fun checkReferences(buildDir: File) {
        // Do not check metadata for kotlinx-atomicfu references if the compiler plugin is applied.
        if (gradleBuild.enableJvmIrTransformation) {
            buildDir.walkDirAndCheckBytecode(skipMetadata = true)    
        } else {
            val atomicfuDir = buildDir.resolve("classes/atomicfu/")
            if (atomicfuDir.exists() && atomicfuDir.isDirectory) {
                atomicfuDir.walkDirAndCheckBytecode(skipMetadata = false)
            }
        }
        
    }
    
    private fun File.walkDirAndCheckBytecode(skipMetadata: Boolean) {
        walkBottomUp().filter { it.isFile && it.name.endsWith(".class") }.forEach { clazz ->
            val atomicfuRefFound = clazz.readBytes().let {
                if (skipMetadata) it.eraseMetadata().findAtomicfuRef() else it.findAtomicfuRef()
            }
            assertFalse(atomicfuRefFound, "Found kotlinx/atomicfu in class file ${clazz.path}")
        }
    }

    // The atomicfu compiler plugin does not remove atomic properties from metadata,
    // so for now we check that there are no ATOMIC_FU_REF left in the class bytecode excluding metadata.
    // This may be reverted after the fix in the compiler plugin transformer (See #254).
    private fun ByteArray.eraseMetadata(): ByteArray {
        val cw = ClassWriter(0)
        ClassReader(this).accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                return if (descriptor == KOTLIN_METADATA_DESC) null else super.visitAnnotation(descriptor, visible)
            }
        }, ClassReader.SKIP_FRAMES)
        return cw.toByteArray()
    }
}

private class KlibChecker(targetDir: File) : ArtifactChecker(targetDir) {

    val nativeJar = System.getProperty("kotlin.native.jar")

    val classLoader: ClassLoader = URLClassLoader(arrayOf(File(nativeJar).toURI().toURL()), this.javaClass.classLoader)

    private fun invokeKlibTool(
        kotlinNativeClassLoader: ClassLoader?,
        klibFile: File,
        functionName: String,
        hasOutput: Boolean,
        vararg args: Any
    ): String {
        val libraryClass = Class.forName("org.jetbrains.kotlin.cli.klib.Library", true, kotlinNativeClassLoader)
        val entryPoint = libraryClass.declaredMethods.single { it.name == functionName }
        val lib = libraryClass.getDeclaredConstructor(String::class.java, String::class.java, String::class.java)
            .newInstance(klibFile.canonicalPath, null, "host")

        val output = StringBuilder()

        // This is a hack. It would be better to get entryPoint properly
        if (args.isNotEmpty()) {
            entryPoint.invoke(lib, output, *args)
        } else if (hasOutput) {
            entryPoint.invoke(lib, output)
        } else {
            entryPoint.invoke(lib)
        }
        return output.toString()
    }

    override fun checkReferences(buildDir: File) {
        val classesDir = buildDir.resolve("classes/kotlin/")
        if (classesDir.exists() && classesDir.isDirectory) {
            classesDir.walkBottomUp().singleOrNull { it.isFile && it.name == "$projectName.klib" }?.let { klib ->
                val klibIr = invokeKlibTool(
                    kotlinNativeClassLoader = classLoader,
                    klibFile = klib,
                    functionName = "ir",
                    hasOutput = true,
                    false
                )
                assertFalse(klibIr.toByteArray().findAtomicfuRef(), "Found kotlinx/atomicfu in klib ${klib.path}:\n $klibIr")
            } ?: error(" Native klib $projectName.klib is not found in $classesDir")
        }
    }
}

internal fun GradleBuild.buildAndCheckBytecode() {
    cleanAndBuild()
    BytecodeChecker(this).checkClassesInBuildDirectories()
}

// TODO: klib checks are skipped for now because of this problem KT-61143
internal fun GradleBuild.buildAndCheckNativeKlib() {
    cleanAndBuild()
    KlibChecker(this.targetDir).checkClassesInBuildDirectories()
}
