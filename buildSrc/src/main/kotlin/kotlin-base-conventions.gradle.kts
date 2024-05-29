import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

val kotlin = extensions.getByType<KotlinProjectExtension>()

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

        optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
}