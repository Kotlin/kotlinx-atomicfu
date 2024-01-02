pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.7.0")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("atomicfuVersion", providers.gradleProperty("atomicfu_version").orNull)
            version("kotlinVersion", providers.gradleProperty("kotlin_version").orNull)
        }
    }
}

rootProject.name = "jdk-compatibility"
