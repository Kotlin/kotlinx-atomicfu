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
        freeCompilerArgs.add("-Xskip-metadata-version-check")

        languageVersion = getOverridingKotlinLanguageVersion(project)?.let { KotlinVersion.fromVersion(it) }
            ?: KotlinVersion.KOTLIN_1_6
        apiVersion = getOverridingKotlinApiVersion(project)?.let { KotlinVersion.fromVersion(it) }
            ?: KotlinVersion.KOTLIN_1_6
    }
}
