/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories

internal fun GradleBuild.buildGradleByShell(
    runIndex: Int,
    commands: List<String>,
    properties: List<String>
): BuildResult {
    val logFile = targetDir.resolve("build-$runIndex.log")

    val gradleCommands = buildSystemCommand(targetDir, commands, properties)

    val builder = ProcessBuilder(gradleCommands)
    builder.directory(gradleWrapperDir)
    builder.redirectErrorStream(true)
    builder.redirectOutput(logFile)
    val process = builder.start()
    val exitCode = process.waitFor()
    return BuildResult(exitCode, logFile)
}

internal fun Path.enableCacheRedirector() {

    val cacheRedirectorPath =
        System.getProperty("cache.redirector.path").ifBlank { null } ?: error("Cache redirector has not been set")

    // Path relative to the current gradle module project dir
    val redirectorScript =
        Paths.get(cacheRedirectorPath)
            .toAbsolutePath()
            .normalize()
            .toFile()

    val gradleDir = resolve("gradle").also { it.createDirectories() }
    redirectorScript.copyTo(gradleDir.resolve("cache-redirector.settings.gradle.kts").toFile())

    val settingsGradle = resolve("settings.gradle")
    val settingsGradleKts = resolve("settings.gradle.kts")
    when {
        Files.exists(settingsGradle) -> settingsGradle.modify {
            """
            |${it.substringBefore("pluginManagement {")}
            |pluginManagement {
            |    apply from: 'gradle/cache-redirector.settings.gradle.kts'
            |${it.substringAfter("pluginManagement {")}
            """.trimMargin()
        }

        Files.exists(settingsGradleKts) -> settingsGradleKts.modify {
            """
            |${it.substringBefore("pluginManagement {")}
            |pluginManagement {
            |    apply(from = "gradle/cache-redirector.settings.gradle.kts")
            |${it.substringAfter("pluginManagement {")}
            """.trimMargin()
        }
    }
}

private fun buildSystemCommand(projectDir: File, commands: List<String>, properties: List<String>): List<String> {
    return if (isWindows)
        listOf("cmd", "/C", "gradlew.bat", "-p", projectDir.canonicalPath) + commands + properties + "--no-daemon"
    else
        listOf("/bin/bash", "gradlew", "-p", projectDir.canonicalPath) + commands + properties + "--no-daemon"
}

private val isWindows: Boolean = System.getProperty("os.name")!!.contains("Windows")

private fun Path.modify(transform: (currentContent: String) -> String) {
    assert(Files.isRegularFile(this)) { "$this is not a regular file!" }

    val file = toFile()
    file.writeText(transform(file.readText()))
}