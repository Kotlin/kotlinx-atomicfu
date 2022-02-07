/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle.test

import kotlinx.atomicfu.plugin.gradle.internal.*
import java.io.File

open class BaseKotlinGradleTest(private val projectName: String) {
    internal val rootProjectDir: File

    init {
        rootProjectDir = File("build${File.separator}test-$projectName").absoluteFile
        rootProjectDir.deleteRecursively()
        rootProjectDir.mkdirs()
    }

    fun checkJvmCompilationClasspath(originalClassFile: String, transformedClassFile: String) {
        // check that test compilation depends on transformed main sources
        val testCompileClasspathFiles = rootProjectDir.filesFrom("build/test_compile_jvm_classpath.txt")
        val testRuntimeClasspathFiles = rootProjectDir.filesFrom("build/test_runtime_jvm_classpath.txt")

        rootProjectDir.resolve(transformedClassFile).let {
            it.checkExists()
            check(it in testCompileClasspathFiles) { "Original '$it' is missing from test compile classpath" }
            check(it in testRuntimeClasspathFiles) { "Original '$it' is missing from test runtime classpath" }
        }

        rootProjectDir.resolve(originalClassFile).let {
            it.checkExists()
            check(it !in testCompileClasspathFiles) { "Transformed '$it' is present in test compile classpath" }
            check(it !in testRuntimeClasspathFiles) { "Transformed '$it' is present in test runtime classpath" }
        }
    }

    fun checkJsCompilationClasspath() {
        // check that test compilation depends on transformed main sources
        val testCompileClasspathFiles = rootProjectDir.filesFrom("build/test_compile_js_classpath.txt")

        rootProjectDir.resolve("build/classes/atomicfu/js/main/$projectName.js").let {
            it.checkExists()
            check(it in testCompileClasspathFiles) { "Original '$it' is missing from test compile classpath" }
        }
    }
}