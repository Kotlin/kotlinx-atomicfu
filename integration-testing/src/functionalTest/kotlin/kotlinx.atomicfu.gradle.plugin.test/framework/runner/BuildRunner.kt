/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

import java.io.File
import java.nio.file.Files

internal class GradleBuild(val projectName: String, val targetDir: File) {
    var enableJvmIrTransformation = false
    var enableJsIrTransformation = false
    var enableNativeIrTransformation = false

    private val properties
        get() = buildList {
            add("-P$KOTLIN_VERSION=$kotlinVersion")
            add("-P${ATOMICFU_VERSION}=$atomicfuVersion")
            add("-P$ENABLE_JVM_IR_TRANSFORMATION=$enableJvmIrTransformation")
            add("-P$ENABLE_JS_IR_TRANSFORMATION=$enableJsIrTransformation")
            add("-P$ENABLE_NATIVE_IR_TRANSFORMATION=$enableNativeIrTransformation")
        }

    private var runCount = 0

    fun runGradle(commands: List<String>): BuildResult =
        buildGradleByShell(runCount++, commands, properties).also {
            require(it.isSuccessful) { "Running $commands on project $projectName FAILED with error:\n" + it.output }
        }
}

internal class BuildResult(exitCode: Int, private val logFile: File) {
    val isSuccessful: Boolean = exitCode == 0

    val output: String by lazy { logFile.readText() }

    // Gets the list of dependencies for the given configuration
    fun getDependenciesForConfig(configuration: String): List<String> {
        val lines = output.lines()
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index++]
            if (line.substringBefore(" ") == configuration) break
        }
        while(index < lines.size) {
            val line = lines[index++]
            if (line.isBlank() || line == "No dependencies") break
            // trim leading indentations (\---) and symbols in the end (*):
            // \--- org.jetbrains.kotlinx:atomicfu:0.22.0-SNAPSHOT (n)
            result.add(line.dropWhile { !it.isLetterOrDigit() }.substringBefore(" "))
        }
        return result
    }
}

internal fun createGradleBuildFromSources(projectName: String): GradleBuild {
    val projectDir = projectExamplesDir.resolve(projectName)
    val targetDir = Files.createTempDirectory("${projectName.substringAfterLast('/')}-").toFile().apply {
        projectDir.copyRecursively(this)
    }
    return GradleBuild(projectName, targetDir)
}
