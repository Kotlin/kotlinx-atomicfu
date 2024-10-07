/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.checker

import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.GradleBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.atomicfuVersion
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.dependencies

private val commonAtomicfuDependency = "org.jetbrains.kotlinx:atomicfu:$atomicfuVersion"
private val jvmAtomicfuDependency = "org.jetbrains.kotlinx:atomicfu-jvm:$atomicfuVersion"

private fun GradleBuild.checkAtomicfuDependencyIsPresent(configurations: List<String>, atomicfuDependency: String) {
    val dependencies = dependencies()
    for (config in configurations) {
        val configDependencies = dependencies.getDependenciesForConfig(config)
        check(configDependencies.contains(atomicfuDependency)) { "Expected $atomicfuDependency in configuration $config, but it was not found." }
    }
}

private fun GradleBuild.checkAtomicfuDependencyIsAbsent(configurations: List<String>, atomicfuDependency: String) {
    val dependencies = dependencies()
    for (config in configurations) {
        val configDependencies = dependencies.getDependenciesForConfig(config)
        check(!configDependencies.contains(atomicfuDependency)) { "Dependency $atomicfuDependency should not be present in the configuration: $config" }
    }
}

internal fun GradleBuild.jvmCheckAtomicfuInCompileClasspath() {
    checkAtomicfuDependencyIsPresent(listOf("compileClasspath"), jvmAtomicfuDependency)
}

internal fun GradleBuild.mppCheckAtomicfuInCompileClasspath(targetName: String) {
    checkAtomicfuDependencyIsPresent(listOf("${targetName}CompileClasspath"), commonAtomicfuDependency)
}

internal fun GradleBuild.mppCheckAtomicfuInRuntimeClasspath(targetName: String) {
    checkAtomicfuDependencyIsPresent(listOf("${targetName}CompileClasspath"), commonAtomicfuDependency)
}

/**
 * There are 4 final configurations:
 * compileClasspath — compile dependencies
 * runtimeClasspath — runtime dependencies
 * apiElements — compile dependencies that will be included in publication
 * runtimeElements — runtime dependencies that will be included in publication
 *
 * The functions below check that `org.jetbrains.kotlinx:atomicfu` dependency is not present in the runtime configurations.
 */

internal fun GradleBuild.jvmCheckNoAtomicfuInRuntimeConfigs() {
    checkAtomicfuDependencyIsAbsent(listOf("runtimeClasspath", "apiElements", "runtimeElements"), jvmAtomicfuDependency)
}

internal fun GradleBuild.mppCheckNoAtomicfuInRuntimeConfigs(targetName: String) {
    checkAtomicfuDependencyIsAbsent(listOf("${targetName}RuntimeClasspath", "${targetName}ApiElements", "${targetName}RuntimeElements"), commonAtomicfuDependency)
}

internal fun GradleBuild.mppCheckAtomicfuInApi(targetName: String) {
    checkAtomicfuDependencyIsPresent(listOf("${targetName}MainApi"), commonAtomicfuDependency)
}

// Checks Native target of an MPP project
internal fun GradleBuild.mppNativeCheckAtomicfuInImplementation() {
    checkAtomicfuDependencyIsPresent(listOf("macosX64MainImplementation"), commonAtomicfuDependency)
}

// Some dependencies may be not resolvable but consumable and will not be present in the output of :dependencies task,
// in this case we should check .pom or .module file of the published project.
// This method checks if the .module file in the sample project publication contains org.jetbrains.kotlinx:atomicfu dependency included.
// It searches for:
// "group": "org.jetbrains.kotlinx",
// "module": "atomicfu-*", atomicfu or atomicfu-jvm
internal fun GradleBuild.checkConsumableDependencies() {
    publishToLocalRepository()
    val moduleFile = getSampleProjectJarModuleFile(targetDir, projectName)
    val lines = moduleFile.readText().lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        if (line.contains("\"group\": \"org.jetbrains.kotlinx\"") &&
            lines[index + 1].contains("\"module\": \"atomicfu")) {
            error("org.jetbrains.kotlinx.atomicfu dependency found in the .module file ${moduleFile.path}")
        }
        index++
    }
}

internal fun GradleBuild.getProjectClasspath(): List<String> =
    buildEnvironment().getDependenciesForConfig("classpath")
