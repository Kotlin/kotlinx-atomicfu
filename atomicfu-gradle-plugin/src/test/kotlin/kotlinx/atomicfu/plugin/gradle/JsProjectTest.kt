/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File

class JsProjectTest : BaseKotlinGradleTest() {
    @Test
    fun testKotlin2JsPlugin() = project("js-simple") {
        val tasksToCheck = arrayOf(
            ":compileKotlin2Js",
            ":compileTestKotlin2Js",
            ":transformAtomicfuJsFiles",
            ":transformTestAtomicfuJsFiles"
        )

        build("build") {
            checkOutcomes(TaskOutcome.SUCCESS, *tasksToCheck)

            val testCompileClasspathFiles = projectDir.resolve("build/test_compile_classpath.txt")
                .readLines().asSequence().flatMap { File(it).walk().filter { it.isFile } }.toHashSet()

            projectDir.resolve("build/classes/kotlin/main/js-simple.js").let {
                it.checkExists()
                check(it in testCompileClasspathFiles) { "Original '$it' is missing from test compile classpath" }
                // todo: check test runtime classpath when js test tasks are supported in plugin
            }

            projectDir.resolve("build/classes/atomicfu/main/js-simple.js").let {
                it.checkExists()
                check(it !in testCompileClasspathFiles) { "Transformed '$it' is present in test compile classpath" }
            }
        }

        build("build") {
            checkOutcomes(TaskOutcome.UP_TO_DATE, *tasksToCheck)
        }
    }
}
