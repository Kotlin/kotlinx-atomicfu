/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

import java.io.File

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

private fun buildSystemCommand(projectDir: File, commands: List<String>, properties: List<String>): List<String> {
    return if (isWindows)
        listOf("cmd", "/C", "gradlew.bat", "-p", projectDir.canonicalPath) + commands + properties
    else
        listOf("/bin/bash", "gradlew", "-p", projectDir.canonicalPath) + commands + properties
}

private val isWindows: Boolean = System.getProperty("os.name")!!.contains("Windows")   
