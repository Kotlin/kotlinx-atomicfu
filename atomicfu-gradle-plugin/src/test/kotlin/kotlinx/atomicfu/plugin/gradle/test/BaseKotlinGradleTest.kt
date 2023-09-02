/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle.test

import kotlinx.atomicfu.plugin.gradle.internal.*
import org.objectweb.asm.*
import java.io.File
import kotlin.test.*

abstract class BaseKotlinGradleTest(private val projectName: String) {
    internal val rootProjectDir: File
    private val ATOMIC_FU_REF = "Lkotlinx/atomicfu/".toByteArray()
    private val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"

    init {
        rootProjectDir = File("build${File.separator}test-$projectName").absoluteFile
        rootProjectDir.deleteRecursively()
        rootProjectDir.mkdirs()
    }

    internal abstract fun BaseKotlinScope.createProject()

    val runner = test {
        createProject()
        runner {
            arguments.add(":build")
            getFileOrNull("kotlin-repo-url.txt")?.let { kotlinRepoURLResource ->
                arguments.add("-Pkotlin_repo_url=${kotlinRepoURLResource.readText()}")
            }
        }
    }

    fun checkTaskOutcomes(executedTasks: List<String>, excludedTasks: List<String>) {
        runner.build().apply {
            val tasks = tasks.map { it.path }
            excludedTasks.forEach {
                check(it !in tasks) { "Post-compilation transformation task $it was added in the compiler plugin mode" }
            }
            executedTasks.forEach {
                assertTaskSuccess(it)
            }
        }
        // check that task outcomes are cached for the second build
        runner.build().apply {
            executedTasks.forEach {
                assertTaskUpToDate(it)
            }
        }
    }

    fun checkJvmCompilationClasspath(originalClassFile: String, transformedClassFile: String) {
        // check that test compile and runtime classpath does not contain original non-transformed classes
        val testCompileClasspathFiles = rootProjectDir.filesFrom("build/test_compile_jvm_classpath.txt")
        val testRuntimeClasspathFiles = rootProjectDir.filesFrom("build/test_runtime_jvm_classpath.txt")

        rootProjectDir.resolve(transformedClassFile).let {
            it.checkExists()
            check(it in testCompileClasspathFiles) { "Transformed '$it' is missing from test compile classpath" }
            check(it in testRuntimeClasspathFiles) { "Transformed '$it' is missing from test runtime classpath" }
        }

        rootProjectDir.resolve(originalClassFile).let {
            it.checkExists()
            check(it !in testCompileClasspathFiles) { "Original '$it' is present in test compile classpath" }
            check(it !in testRuntimeClasspathFiles) { "Original '$it' is present in test runtime classpath" }
        }
    }

    fun checkJsCompilationClasspath() {
        // check that test compilation depends on transformed main sources
        val testCompileClasspathFiles = rootProjectDir.filesFrom("build/test_compile_js_classpath.txt")

        rootProjectDir.resolve("build/classes/atomicfu/js/main/$projectName.js").let {
            it.checkExists()
            check(it in testCompileClasspathFiles) { "Transformed '$it' is missing from test compile classpath" }
        }
    }

    fun checkBytecode(classFilePath: String) {
        rootProjectDir.resolve(classFilePath).let {
            it.checkExists()
            assertFalse(it.readBytes().findAtomicfuRef(), "Found 'Lkotlinx/atomicfu/' reference in $it" )
        }
    }

    private fun ByteArray.findAtomicfuRef(): Boolean {
        val bytes = this.eraseMetadata()
        loop@for (i in 0 until bytes.size - ATOMIC_FU_REF.size) {
            for (j in 0 until ATOMIC_FU_REF.size) {
                if (bytes[i + j] != ATOMIC_FU_REF[j]) continue@loop
            }
            return true
        }
        return false
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
