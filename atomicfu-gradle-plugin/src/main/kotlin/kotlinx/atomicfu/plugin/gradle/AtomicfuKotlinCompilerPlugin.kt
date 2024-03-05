package kotlinx.atomicfu.plugin.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*

/**
 * The Gradle plugin that applies JVM and JS IR transformations performed by the atomicfu compiler plugin.
 */
internal class AtomicfuKotlinCompilerPlugin : AbstractAtomicfuKotlinCompilerPlugin() {

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val target = kotlinCompilation.target
        val project = target.project
        return project.needsJvmIrTransformation(target) || project.needsJsIrTransformation(target)
    }

    override fun getPluginArtifact(): SubpluginArtifact {
        return JetBrainsSubpluginArtifact(ATOMICFU_ARTIFACT_NAME)
    }
}

/**
 * The Gradle plugin that applies Native IR transformations performed by the atomicfu compiler plugin.
 * Unlike [AtomicfuKotlinCompilerPlugin] it extracts kotlin.native.version, because it may be overriden and differ from the KGP version.
 */
internal class AtomicfuKotlinNativeCompilerPlugin : AbstractAtomicfuKotlinCompilerPlugin() {

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val target = kotlinCompilation.target
        val project = target.project
        return project.needsNativeIrTransformation(target)
    }

    // TODO: also pay attention that kotlin native version of the plugin may not resolved (if only compiler was built), then print a warning
    override fun getPluginArtifact(): SubpluginArtifact {
        // todo extract kotlin.native.version here
        // Extract kotlin.native.version here, it may differ from the KGP version
        return SubpluginArtifact(GROUP_NAME, ATOMICFU_ARTIFACT_NAME)
    }
}

/**
 * This Gradle plugin applies compiler transformations to the project, it was copied from the kotlin repo (org.jetbrains.kotlinx.atomicfu.gradle.AtomicfuKotlinGradleSubplugin).
 * 
 * As the sources of the compiler plugin are published as `org.jetbrains.kotlin.kotlin-atomicfu-compiler-plugin-embeddable` starting from Kotlin 1.9.0,
 * the Gradle plugin can access this artifact from the library and apply the transformations.
 */
internal sealed class AbstractAtomicfuKotlinCompilerPlugin : KotlinCompilerPluginSupportPlugin {
    
    companion object {
        const val GROUP_NAME = "org.jetbrains.kotlin"
        const val ATOMICFU_ARTIFACT_NAME = "kotlin-atomicfu-compiler-plugin-embeddable"
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }
    
    override fun getCompilerPluginId() = "org.jetbrains.kotlinx.atomicfu"

    override fun getPluginArtifact(): SubpluginArtifact {
        return JetBrainsSubpluginArtifact(ATOMICFU_ARTIFACT_NAME)
    }
}
