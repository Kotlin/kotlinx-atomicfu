rootProject.name = "kotlinx-atomicfu"

pluginManagement {
    includeBuild("build-settings-logic")

    repositories {
        // gradlePluginPortal(), cache-redirected
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
    }
}

plugins {
    id("atomicfu-dependency-resolution-management")
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
    id("atomicfu-develocity")
    id("atomicfu-cache-redirector")
    id("org.jetbrains.kotlinx.artifacts-validator-plugin") version("0.0.2")
}

include("atomicfu")
include("atomicfu-transformer")
include("atomicfu-gradle-plugin")
include("atomicfu-maven-plugin")

include("integration-testing")
