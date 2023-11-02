/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

internal fun GradleBuild.cleanAndBuild(): BuildResult = runGradle(listOf("clean", "build"))

internal fun GradleBuild.dependencies(): BuildResult = runGradle(listOf("dependencies"))

internal fun GradleBuild.publishToLocalRepository(): BuildResult =
    runGradle(listOf("clean", "publishMavenPublicationToLocalRepository"))
