import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm")
}

project.extra["kotlin.compiler.runViaBuildToolsApi"] = "true"

kotlin {
    @OptIn(ExperimentalBuildToolsApi::class, ExperimentalKotlinGradlePluginApi::class)
    compilerVersion.set(
        versionCatalogs
            .named("libs")
            .findVersion("kotlin-for-gradle-plugin")
            .get()
            .requiredVersion
    )

    // Gradle plugin must be compiled targeting the same Kotlin version as used by Gradle
    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    compilerOptions {
        setWarningsAsErrors(project)
        freeCompilerArgs.add("-Xsuppress-version-warnings")

        // We're using a fixed Kotlin version for compatibility with older Gradle versions.
        // As a result, a set of support LV and AV values is limited, and we can't take those
        // coming from getOverridingKotlinLanguageVersion / getOverridingKotlinApiVersion
        // as they tend to be "too new" nowadays.
        languageVersion = KotlinVersion.KOTLIN_1_6
        apiVersion = KotlinVersion.KOTLIN_1_6
    }
}
