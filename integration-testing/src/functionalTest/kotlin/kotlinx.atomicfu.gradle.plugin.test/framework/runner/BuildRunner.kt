/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

import java.io.File
import java.nio.file.Files

internal interface GradleBuild {
    val projectName: String

    val targetDir: File

    var enableJvmIrTransformation: Boolean

    var enableJsIrTransformation: Boolean

    var enableNativeIrTransformation: Boolean

    fun runGradle(commands: List<String>): BuildResult

    fun clear()
}

private class GradleBuildImpl(
    override val projectName: String,
    override val targetDir: File
) : GradleBuild {

    override var enableJvmIrTransformation = false
    override var enableJsIrTransformation = false
    override var enableNativeIrTransformation = false

    private val properties
        get() = buildList {
            add("-P$ENABLE_JVM_IR_TRANSFORMATION=$enableJvmIrTransformation")
            add("-P$ENABLE_JS_IR_TRANSFORMATION=$enableJsIrTransformation")
            add("-P$ENABLE_NATIVE_IR_TRANSFORMATION=$enableNativeIrTransformation")
        }

    private var runCount = 0

    override fun runGradle(commands: List<String>): BuildResult =
        buildGradleByShell(runCount++, commands, properties).also {
            require(it.isSuccessful) { "Running $commands on project $projectName FAILED with error:\n" + it.output }
        }

    override fun clear() { targetDir.deleteRecursively() }
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
            if (line.takeWhile { it.isLetter() } == configuration) break
        }
        while(index < lines.size) {
            val line = lines[index++]
            if (line.isBlank()) break
            // trim leading indentations (\---) and symbols in the end (*):
            // \--- org.jetbrains.kotlinx:atomicfu:0.22.0-SNAPSHOT (n)
            result.add(line.dropWhile { !it.isLetter() }.substringBefore(" "))
        }
        return result
    }
}

internal fun createGradleBuildFromSources(projectName: String): GradleBuild {
    val projectDir = projectExamplesDir.resolve(projectName)
    val targetDir = Files.createTempDirectory("${projectName.substringAfterLast('/')}-").toFile().apply {
        projectDir.copyRecursively(this)
    }
    return GradleBuildImpl(projectName, targetDir)
}