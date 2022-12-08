@file:JvmName("KotlinAggregateBuild")

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.*
import java.net.*
import java.util.logging.Logger

/*
 * Functions in this file are responsible for configuring atomicfu build against a custom dev version
 * of Kotlin compiler.
 * Such configuration is used in aggregate builds of Kotlin in order to check whether not-yet-released changes
 * are compatible with our libraries (aka "integration testing that substitues lack of unit testing").
 */

private val LOGGER: Logger = Logger.getLogger("Kotlin settings logger")

/**
 * Should be used for running against of non-released Kotlin compiler on a system test level.
 *
 * @return a Kotlin API version parametrized from command line nor gradle.properties, null otherwise
 */
fun getOverriddenKotlinApiVersion(project: Project): String? {
    val apiVersion = project.rootProject.properties["kotlin_api_version"] as? String
    if (apiVersion != null) {
        LOGGER.info("""Configured Kotlin API version: '$apiVersion' for project $${project.name}""")
    }
    return apiVersion
}

/**
 * Should be used for running against of non-released Kotlin compiler on a system test level
 *
 * @return a Kotlin Language version parametrized from command line nor gradle.properties, null otherwise
 */
fun getOverriddenKotlinLanguageVersion(project: Project): String? {
    val languageVersion = project.rootProject.properties["kotlin_language_version"] as? String
    if (languageVersion != null) {
        LOGGER.info("""Configured Kotlin Language version: '$languageVersion' for project ${project.name}""")
    }
    return languageVersion
}

/**
 * Should be used for running against of non-released Kotlin compiler on a system test level
 * Kotlin compiler artifacts are expected to be downloaded from maven central by default.
 * In case of compiling with not-published into the MC kotlin compiler artifacts, a kotlin_repo_url gradle parameter should be specified.
 * To reproduce a build locally, a kotlin/dev repo should be passed
 *
 * @return an url for a kotlin compiler repository parametrized from command line nor gradle.properties, empty string otherwise
 */
fun getKotlinDevRepositoryUrl(project: Project): String? {
    val url = project.rootProject.properties["kotlin_repo_url"] as? String
    if (url != null) {
        LOGGER.info("""Configured Kotlin Compiler repository url: '$url' for project ${project.name}""")
    }
    return url
}

/**
 * Adds a kotlin-dev space repository with dev versions of Kotlin if Kotlin aggregate build is enabled
 */
fun addDevRepositoryIfEnabled(rh: RepositoryHandler, project: Project) {
    val devRepoUrl = getKotlinDevRepositoryUrl(project) ?: return
    rh.maven {
        url = URI.create(devRepoUrl)
    }
}
