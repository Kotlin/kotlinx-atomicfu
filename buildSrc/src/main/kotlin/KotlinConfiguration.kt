@file:JvmName("KotlinConfiguration")

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import java.util.logging.Logger

/*
 * Functions in this file are responsible for configuring atomicfu build
 * against a custom development version of Kotlin compiler.
 * Such configuration is used in Kotlin aggregate builds and builds of Kotlin user projects
 * in order to check whether not-yet-released changes are compatible with our libraries
 * (aka "integration testing that substitutes lack of unit testing").
 */

private val LOGGER: Logger = Logger.getLogger("Kotlin settings logger")

/**
 * Should be used for running against a non-released Kotlin compiler on a system test level.
 *
 * @return a custom repository with development builds of the Kotlin compiler taken from:
 *
 * 1. the Kotlin community project Gradle plugin,
 * 2. or `kotlin_repo_url` Gradle property (from command line or from `gradle.properties`),
 *
 * or null otherwise
 */
fun getCustomKotlinRepositoryURL(project: Project): String? {
    val gradlePropertyKotlinRepoURL = project.findProperty("kotlin_repo_url") as? String
    val kotlinRepoURL = when {
        gradlePropertyKotlinRepoURL != null -> gradlePropertyKotlinRepoURL
        else -> return null
    }
    LOGGER.info("A custom Kotlin repository $kotlinRepoURL was found for project ${project.name}")
    return kotlinRepoURL
}

/**
 * Should be used for running against a non-released Kotlin compiler on a system test level.
 *
 * @return a Kotlin language version taken from:
 *
 * 1. the Kotlin community project Gradle plugin,
 * 2. or `kotlin_language_version` Gradle property (from command line or from `gradle.properties`),
 *
 * or null otherwise
 */
fun getOverridingKotlinLanguageVersion(project: Project): String? {
    val providers = project.providers
    val languageVersion = providers.gradleProperty("community.project.kotlin.languageVersion")
        .orElse(providers.gradleProperty("kotlin_language_version"))
        .orNull
    LOGGER.info("An overriding Kotlin language version of $languageVersion was found for project ${project.name}")
    return languageVersion
}

/**
 * Should be used for running against a non-released Kotlin compiler on a system test level.
 *
 * @return a Kotlin API version taken from:
 *
 * 1. the Kotlin community project Gradle plugin,
 * 2. or `kotlin_language_version` Gradle property (from command line or from `gradle.properties`),
 *
 * or null otherwise
 */
fun getOverridingKotlinApiVersion(project: Project): String? {
    val providers = project.providers
    val apiVersion = providers.gradleProperty("community.project.kotlin.apiVersion")
        .orElse(providers.gradleProperty("kotlin_api_version"))
        .orNull
    LOGGER.info("An overriding Kotlin api version of $apiVersion was found for project ${project.name}")
    return apiVersion
}

/**
 * Should be used for testing that the Kotlin compiler produces the correct IR.
 *
 * Can be enabled via passing the `kotlin_ir_validation_mode` Gradle property
 * (from command line or from `gradle.properties`).
 *
 * The mode is a string passed to the `-Xverify-ir=...` compiler CLI option.
 */
fun irValidationMode(project: Project): String? =
    project.providers.gradleProperty("kotlin_ir_validation_mode").orNull

/**
 * Unconditional compiler flags required of Kotlin User Projects. See KT-75078.
 */
fun KotlinCommonCompilerOptions.addKotlinUserProjectFlags() {
    freeCompilerArgs.addAll(
        "-Xreport-all-warnings",
        "-Xrender-internal-diagnostic-names",
    )
}

/**
 * Use `kotlin_Werror_override` property to override the state of -Werror:
 * `true` means that warnings should be treated as errors,`false` means that they should not.
 */
private fun warningsAreErrorsOverride(project: Project): Boolean? =
    project.providers.gradleProperty("kotlin_Werror_override").orNull.let {
        when (it) {
            "enable" -> true
            "disable" -> false
            null -> true
            else -> throw GradleException("Invalid kotlin_Werror_override value. Use 'enable' or 'disable'")
        }
    }

/**
 * Set warnings as errors but allow the Kotlin User Project configuration to override. See KT-75078.
 */
fun KotlinCommonCompilerOptions.setWarningsAsErrors(project: Project) {
    if (warningsAreErrorsOverride(project) == false) {
        freeCompilerArgs.addAll("-Wextra", "-Xuse-fir-experimental-checkers")
    } else {
        allWarningsAsErrors.set(true)
    }
}

/**
 * Pass additional CLI options to the Kotlin compiler.
 */
fun KotlinCommonCompilerOptions.addExtraCompilerFlags(project: Project) {
    project.providers.gradleProperty("kotlin_additional_cli_options").orNull?.let { options ->
        options.split(" ").filter { it.isNotBlank() }.forEach {
            freeCompilerArgs.add(it)
        }
    }
}