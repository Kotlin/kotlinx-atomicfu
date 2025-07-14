/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

import java.io.File
import java.nio.file.Files

/**
 * @param[targetDir] The root Gradle project directory.
 */
internal class GradleBuild(val projectName: String, val targetDir: File) {
    var enableJvmIrTransformation = false
    var enableJsIrTransformation = false
    var enableNativeIrTransformation = false
    var localRepositoryUrl: String? = null
    val extraProperties = mutableListOf<String>()

    private val properties
        get() = buildList {
            add("-P$ENABLE_JVM_IR_TRANSFORMATION=$enableJvmIrTransformation")
            add("-P$ENABLE_JS_IR_TRANSFORMATION=$enableJsIrTransformation")
            add("-P$ENABLE_NATIVE_IR_TRANSFORMATION=$enableNativeIrTransformation")
            // Pass kotlin_version and kotlin.native.version parameters used for the library build to the builds of integration tests.
            // These versions may be overriden by the TC build config.
            if (kotlinVersion.isNotEmpty()) add("-P$KOTLIN_VERSION_PARAMETER=$kotlinVersion")
            if (atomicfuVersion.isNotEmpty()) add("-P$ATOMICFU_VERSION=$atomicfuVersion")
            if (kotlinNativeVersion.isNotEmpty()) add("-P$KOTLIN_NATIVE_VERSION_PARAMETER=$kotlinNativeVersion")
            localRepositoryUrl?.let { add("-P$LOCAL_REPOSITORY_URL_PROPERTY=$it") }
            addAll(extraProperties)
        }

    private var runCount = 0

    fun runGradle(commands: List<String>): BuildResult = buildGradleByShell(runCount++, commands, properties)
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
        while (index < lines.size) {
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

    targetDir.toPath().enableCacheRedirector()

    kotlinArtifactsRepo?.let {
        // kotlinArtifactsRepo contains the directory on the TC agent, where Kotlin artifacts were published
        // (this directory was passed as kotlin_repo_url parameter to the library build).

        // Add maven(url =  file:///mnt/agent/work/...) repository in build.gradle and settings.gradle files of all integration tests.
        targetDir.walkTopDown()
            .filter { it.isFile && (it.name.startsWith("build.gradle") || it.name.startsWith("settings.gradle")) }
            .forEach { buildGradleFile ->
                buildGradleFile.addKotlinArtifactRepositoryToProjectBuild(kotlinArtifactsRepo)
            }
    }
    return GradleBuild(projectName, targetDir)
}
