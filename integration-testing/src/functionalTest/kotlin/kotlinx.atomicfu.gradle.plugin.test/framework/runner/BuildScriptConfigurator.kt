/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

import java.io.*

/**
 * This function adds a given repository to the repositories {} blocks declared in build files:
 * // settings.gradle:
 * pluginManagement {
 *   repositories {
 *      maven(url = "file:///mnt/agent/work/../artifacts/kotlin")
 *      mavenCentral()
 *   }
 * }
 *
 * // build.gradle:
 * repositories {
 *   maven(url = "file:///mnt/agent/work/../artifacts/kotlin")
 * }
 */
internal fun File.addKotlinArtifactRepositoryToProjectBuild(
    repository: String
) {
    val isKts = name.endsWith(".kts")
    val originLines = readLines()
    var previousLine = ""

    bufferedWriter().use { writer ->
        originLines.forEach { line ->
            writer.appendLine(line)
            // in build.gradle exclude publishing { repositories {...} } block
            val isInPublishingBlock = previousLine.trimStart().startsWith("publishing")
            // in settings.gradle add repository only in pluginManagement {...} block
            val isInPluginManagementBlock = line.trimStart().startsWith("pluginManagement")
            val isRepositoryBlock = line.trimStart().startsWith("repositories")
            if (line.isNotBlank() && ((isInPluginManagementBlock && isRepositoryBlock) || (!isInPublishingBlock && isRepositoryBlock))) {
                if (isKts) {
                    writer.appendLine("maven(url = \"$repository\")")
                } else {
                    writer.appendLine("maven { url '$repository' }")
                }
            }
            previousLine = line
        }
    }
}
