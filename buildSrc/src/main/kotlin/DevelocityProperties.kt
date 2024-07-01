/*
 * Copyright 2014-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import javax.inject.Inject


const val BUILD_SCAN_USERNAME_DEFAULT = "<default>"

val buildingOnTeamCity: Boolean = System.getenv("TEAMCITY_VERSION") != null
val buildingOnGitHub: Boolean = System.getenv("GITHUB_ACTION") != null
val buildingOnCi: Boolean = System.getenv("CI") != null || buildingOnTeamCity || buildingOnGitHub

// NOTE: build scan properties are documented in CONTRIBUTING.md
fun buildScanEnabled(project: Project): Provider<Boolean> =
    atomicfuProperty("build.scan.enabled", project, String::toBoolean)
        .orElse(buildingOnCi)

/**
 * Optionally override the default name attached to a Build Scan.
 */
fun buildScanUsername(project: Project): Provider<String> =
    atomicfuProperty("build.scan.username", project)
        .orElse(BUILD_SCAN_USERNAME_DEFAULT)
        .map(String::trim)

private fun atomicfuProperty(name: String, project: Project): Provider<String> =
    project.providers.gradleProperty("org.jetbrains.atomicfu.$name")

private fun <T : Any> atomicfuProperty(name: String, project: Project, convert: (String) -> T): Provider<T> =
    atomicfuProperty(name, project).map(convert)

