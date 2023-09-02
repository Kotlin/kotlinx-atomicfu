@file:JvmName("KotlinConfiguration")

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.net.URI
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
    val communityPluginKotlinRepoURL = project.findProperty("community.project.kotlin.repo") as? String
    val gradlePropertyKotlinRepoURL = project.findProperty("kotlin_repo_url") as? String
    val kotlinRepoURL = when {
        communityPluginKotlinRepoURL != null -> communityPluginKotlinRepoURL
        gradlePropertyKotlinRepoURL != null -> gradlePropertyKotlinRepoURL
        else -> return null
    }
    LOGGER.info("A custom Kotlin repository $kotlinRepoURL was found for project ${project.name}")
    return kotlinRepoURL
}

/**
 * Should be used for running against a non-released Kotlin compiler on a system test level.
 *
 * Adds a custom repository with development builds of the Kotlin compiler to [repositoryHandler]
 * if the URL is provided (see [getCustomKotlinRepositoryURL]).
 */
fun addCustomKotlinRepositoryIfEnabled(repositoryHandler: RepositoryHandler, project: Project) {
    val kotlinRepoURL = getCustomKotlinRepositoryURL(project) ?: return
    repositoryHandler.maven { url = URI.create(kotlinRepoURL) }

}

/**
 * Should be used for running against a non-released Kotlin compiler on a system test level.
 *
 * @return a Kotlin version taken from the Kotlin community project Gradle plugin,
 *         or null otherwise
 */
fun getOverridingKotlinVersion(project: Project): String? {
    val communityPluginKotlinVersion = project.findProperty("community.project.kotlin.version") as? String
    // add any other ways of overriding the Kotlin version here
    val kotlinVersion = when {
        communityPluginKotlinVersion != null -> communityPluginKotlinVersion
        // add any other ways of overriding the Kotlin version here
        else -> return null
    }
    LOGGER.info("An overriding Kotlin version of $kotlinVersion was found for project ${project.name}")
    return kotlinVersion
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
    val communityPluginLanguageVersion = project.findProperty("community.project.kotlin.languageVersion") as? String
    val gradlePropertyLanguageVersion = project.findProperty("kotlin_language_version") as? String
    val languageVersion = when {
        communityPluginLanguageVersion != null -> communityPluginLanguageVersion
        gradlePropertyLanguageVersion != null -> gradlePropertyLanguageVersion
        else -> return null
    }
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
    val communityPluginApiVersion = project.findProperty("community.project.kotlin.apiVersion") as? String
    val gradlePropertyApiVersion = project.findProperty("kotlin_api_version") as? String
    val apiVersion = when {
        communityPluginApiVersion != null -> communityPluginApiVersion
        gradlePropertyApiVersion != null -> gradlePropertyApiVersion
        else -> return null
    }
    LOGGER.info("An overriding Kotlin api version of $apiVersion was found for project ${project.name}")
    return apiVersion
}
