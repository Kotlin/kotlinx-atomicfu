/*
 * Copyright 2014-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider


val buildingOnTeamCity: Boolean = System.getenv("TEAMCITY_VERSION") != null
val buildingOnGitHub: Boolean = System.getenv("GITHUB_ACTION") != null
val buildingOnCi: Boolean = System.getenv("CI") != null || buildingOnTeamCity || buildingOnGitHub

// NOTE: build scan properties are documented in README.md
val Settings.buildScanEnabled: Provider<Boolean>
    get() =
        atomicfuProperty("build.scan.enabled", String::toBoolean)
            .orElse(buildingOnCi)

/**
 * Optionaly override the default name attached to a Build Scan.
 */
val Settings.buildScanUsername: Provider<String>
    get() =
        atomicfuProperty("build.scan.username")
            .map(String::trim)

internal fun Settings.atomicfuProperty(name: String): Provider<String> =
    providers.gradleProperty("org.jetbrains.atomicfu.$name")

internal fun <T : Any> Settings.atomicfuProperty(name: String, convert: (String) -> T): Provider<T> =
    atomicfuProperty(name).map(convert)

