/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*

/**
 * This Gradle plugin applies compiler transformations to the project, it was copied from the kotlin repo (org.jetbrains.kotlinx.atomicfu.gradle.AtomicfuKotlinGradleSubplugin).
 *
 * As the sources of the compiler plugin are published as `org.jetbrains.kotlin.kotlin-atomicfu-compiler-plugin-embeddable` starting from Kotlin 1.9.0,
 * the Gradle plugin can access this artifact from the library and apply the transformations.
 *
 * NOTE: The version of KGP may differ from the version of Kotlin compiler, and kotlin.native.version may override the version of Kotlin native compiler.
 * So, the right behavior for the Gradle plugin would be to obtain compiler versions and apply compiler transformations separately to JVM/JS and Native targets.
 * This was postponed as a separate task (#408).
 */
internal class AtomicfuKotlinCompilerPluginInternal : KotlinCompilerPluginSupportPlugin {
    
    companion object {
        const val ATOMICFU_ARTIFACT_NAME = "kotlin-atomicfu-compiler-plugin-embeddable"
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val target = kotlinCompilation.target
        val targetPlatformType = target.platformType
        val project = target.project
        return project.needsJvmIrTransformation(targetPlatformType) || project.needsJsIrTransformation(targetPlatformType) || project.needsNativeIrTransformation(targetPlatformType)
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> = kotlinCompilation.target.project.provider { emptyList() }

    override fun getCompilerPluginId() = "org.jetbrains.kotlin.atomicfu"

    // Gets "org.jetbrains.kotlin:kotlin-atomicfu-compiler-plugin-embeddable:{KGP version}"
    override fun getPluginArtifact(): SubpluginArtifact {
        return JetBrainsSubpluginArtifact(ATOMICFU_ARTIFACT_NAME)
    }
}
