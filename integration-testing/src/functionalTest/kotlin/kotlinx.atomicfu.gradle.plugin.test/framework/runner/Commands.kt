/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.getProjectClasspath

internal fun GradleBuild.build(): BuildResult =
    runGradle(listOf("build")).also {
        require(it.isSuccessful) { "${this.projectName}:build task FAILED: ${it.output} " }
    }

internal fun GradleBuild.cleanAndBuild(): BuildResult =
    runGradle(listOf("clean", "build")).also {
        require(it.isSuccessful) { "${this.projectName}:build task FAILED: ${it.output} " }
    }

internal fun GradleBuild.cleanAndJar(): BuildResult = runGradle(listOf("clean", "jar"))

internal fun GradleBuild.dependencies(): BuildResult =
    runGradle(listOf("dependencies")).also {
        require(it.isSuccessful) { "${this.projectName}:dependencies task FAILED: ${it.output} " }
    }

internal fun GradleBuild.buildEnvironment(): BuildResult =
    runGradle(listOf("buildEnvironment")).also {
        require(it.isSuccessful) { "${this.projectName}:buildEnvironment task FAILED: ${it.output} " }
    }

internal fun GradleBuild.publishToLocalRepository(): BuildResult =
    runGradle(listOf("clean", "publish")).also {
        require(it.isSuccessful) { "${this.projectName}:publish task FAILED: ${it.output} " }
    }

internal fun GradleBuild.buildAndPublishToLocalRepository(): BuildResult =
    runGradle(listOf("clean", "build", "publish")).also {
        require(it.isSuccessful) { "${this.projectName}:publish task FAILED: ${it.output} " }
    }

internal fun GradleBuild.getKotlinVersion(): String {
    val classpath = getProjectClasspath()
    val kpg = classpath.firstOrNull { it.startsWith("org.jetbrains.kotlin:kotlin-gradle-plugin") }
        ?: error("kotlin-gradle-plugin is not found in the classpath of the project $projectName")
    return kpg.substringAfterLast(":")
}
