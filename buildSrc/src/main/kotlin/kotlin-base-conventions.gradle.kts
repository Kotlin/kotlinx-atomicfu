import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

val kotlin = extensions.getByType<KotlinProjectExtension>()

val deployVersion: String? = project.findProperty("DeployVersion")?.toString()?.ifBlank { null }
if (deployVersion != null) project.version = deployVersion

kotlin.sourceSets.configureEach {
    languageSettings {
        val overridingKotlinLanguageVersion = getOverridingKotlinLanguageVersion(project)
        if (overridingKotlinLanguageVersion != null) {
            languageVersion = overridingKotlinLanguageVersion
        }

        val overridingKotlinApiVersion = getOverridingKotlinApiVersion(project)
        if (overridingKotlinApiVersion != null) {
            apiVersion = overridingKotlinApiVersion
        }

        if (project.path != ":atomicfu-transformer" &&
            project.path != ":atomicfu-gradle-plugin"
        ) {
            optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
    }
}