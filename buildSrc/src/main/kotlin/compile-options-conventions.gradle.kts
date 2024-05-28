import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
// TODO https://github.com/Kotlin/kotlinx-atomicfu/issues/421
//      convert this script to a kotlin-jvm convention
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