rootProject.name = "kotlinx-atomicfu"

pluginManagement {
    includeBuild("build-settings-logic")
}

plugins {
    id("atomicfu-dependency-resolution-management")
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
    id("atomicfu-gradle-build-scan")
    id("atomicfu-gradle-build-cache")
    id("atomicfu-cache-redirector")
}

include("atomicfu")
include("atomicfu-transformer")
include("atomicfu-gradle-plugin")
include("atomicfu-maven-plugin")

include("integration-testing")