/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.*
import java.io.File

class MppProjectTest : BaseKotlinGradleTest() {
    @Ignore
    @Test
    fun testKotlinMultiplatformPlugin() = project("mpp-simple") {
        val tasksToCheck = arrayOf(
            ":compileKotlinJvm",
            ":compileTestKotlinJvm",
            ":transformJvmMainAtomicfu",
            ":transformJvmTestAtomicfu",
            ":compileKotlinJs",
            ":transformJsMainAtomicfu"
        )

        build("build") {
            checkOutcomes(TaskOutcome.SUCCESS, *tasksToCheck)

            fun checkPlatform(platform: String, fileInMainName: String) {
                val isJs = platform == "js"
                val testCompileClasspathFiles = projectDir.resolve("build/classpath/$platform/test_compile.txt")
                    .readLines().asSequence().flatMapTo(HashSet()) { File(it).walk().filter { it.isFile } }
                val testRuntimeClasspathFiles = if (isJs) emptySet<File>() else projectDir.resolve("build/classpath/$platform/test_runtime.txt")
                    .readLines().asSequence().flatMapTo(HashSet()) { File(it).walk().filter { it.isFile } }

                projectDir.resolve("build/classes/kotlin/$platform/main/$fileInMainName").let {
                    it.checkExists()
                    check(it in testCompileClasspathFiles) { "Original '$it' is missing from $platform test compile classpath" }
                    if (!isJs) check(it in testRuntimeClasspathFiles) { "Original '$it' is missing from $platform test runtime classpath" }
                }

                projectDir.resolve("build/classes/atomicfu/jvm/main/IntArithmetic.class").let {
                    it.checkExists()
                    check(it !in testCompileClasspathFiles) { "Transformed '$it' is present in $platform test compile classpath" }
                    if (!isJs) check(it !in testRuntimeClasspathFiles) { "Transformed '$it' is present in $platform test runtime classpath" }
                }

            }

            checkPlatform("jvm", "IntArithmetic.class")
            checkPlatform("js", "mpp-simple.js")
        }

        build("build") {
            checkOutcomes(TaskOutcome.UP_TO_DATE, *tasksToCheck)
        }
    }
}
