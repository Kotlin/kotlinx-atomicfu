/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.framework.runner

import java.io.File

internal const val ENABLE_JVM_IR_TRANSFORMATION = "kotlinx.atomicfu.enableJvmIrTransformation"
internal const val ENABLE_JS_IR_TRANSFORMATION = "kotlinx.atomicfu.enableJsIrTransformation"
internal const val ENABLE_NATIVE_IR_TRANSFORMATION = "kotlinx.atomicfu.enableNativeIrTransformation"
internal const val LOCAL_REPOSITORY_URL_PROPERTY = "localRepositoryUrl"
internal const val DUMMY_VERSION = "DUMMY_VERSION"
internal const val LOCAL_REPO_DIR_PREFIX = "build/.m2/"

internal val libraryKotlinVersion = System.getProperty("kotlinVersion")
internal val atomicfuVersion = System.getProperty("atomicfuVersion")

internal val gradleWrapperDir = File("..")

internal val projectExamplesDir = File("examples")

internal fun getLocalRepoDir(targetDir: File): File =
    targetDir.resolve(LOCAL_REPO_DIR_PREFIX).also {
        require(it.exists() && it.isDirectory) { "Could not find local repository `$LOCAL_REPO_DIR_PREFIX` in the project directory: ${targetDir.path}" }
    }

// The project is published in the local repo directory /build/.m2/ with DUMMY_VERSION
internal fun getSampleProjectJarModuleFile(targetDir: File, projectName: String): File =
    getLocalRepoDir(targetDir).resolve("kotlinx/atomicfu/examples/$projectName/$DUMMY_VERSION").walkBottomUp()
        .singleOrNull { it.name.endsWith(".module") }  ?: error("Could not find jar module file in local repository of the project $projectName: ${getLocalRepoDir(targetDir)}")
