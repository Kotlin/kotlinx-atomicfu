/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

rootProject.name = "mpp-version-catalog"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
    versionCatalogs {
        create("libs") {
            val atomicfuVersion = providers.gradleProperty("atomicfu_version").orNull
            if (atomicfuVersion != null) {
                version("atomicfu", atomicfuVersion)
            }
            val kotlinVersion = providers.gradleProperty("kotlin_version").orNull
            if (kotlinVersion != null) {
                version("kotlin", kotlinVersion)
            }
        }
    }
}

include(":shared")
