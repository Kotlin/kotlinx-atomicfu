/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File

class JvmProjectTest : BaseKotlinGradleTest() {
    @Test
    fun testKotlinPlugin() =
        project("jvm-simple") {
            doSimpleTest()
        }

    @Test
    fun testKotlinPlatformJvmPlugin() =
        project("jvm-simple", "-platform") {
            projectDir.resolve("build.gradle").modify {
                it.checkedReplace("apply plugin: 'kotlin'", "apply plugin: 'kotlin-platform-jvm'")
            }
            doSimpleTest()
        }

    private fun Project.doSimpleTest() {
        val tasksToCheck = arrayOf(
            ":compileKotlin",
            ":compileTestKotlin",
            ":transformAtomicfuClasses",
            ":transformTestAtomicfuClasses"
        )

        build("build") {
            checkOutcomes(TaskOutcome.SUCCESS, *tasksToCheck)

            val testCompileClasspathFiles = filesFrom("build/test_compile_classpath.txt")
            val testRuntimeClasspathFiles = filesFrom("build/test_runtime_classpath.txt")

            projectDir.resolve("build/classes/kotlin/main/IntArithmetic.class").let {
                it.checkExists()
                check(it in testCompileClasspathFiles) { "Original '$it' is missing from test compile classpath" }
                check(it in testRuntimeClasspathFiles) { "Original '$it' is missing from test runtime classpath" }
            }

            projectDir.resolve("build/classes/atomicfu/main/IntArithmetic.class").let {
                it.checkExists()
                check(it !in testCompileClasspathFiles) { "Transformed '$it' is present in test compile classpath" }
                check(it !in testRuntimeClasspathFiles) { "Transformed '$it' is present in test runtime classpath" }
            }
        }

        build("build") {
            checkOutcomes(TaskOutcome.UP_TO_DATE, *tasksToCheck)
        }
    }

    private fun Project.filesFrom(name: String) = projectDir.resolve(name)
        .readLines().asSequence().flatMap { listFiles(it) }.toHashSet()

    private fun listFiles(dir: String): Sequence<File> = File(dir).walk().filter { it.isFile }
}
