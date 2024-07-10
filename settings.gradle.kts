rootProject.name = "kotlinx-atomicfu"

pluginManagement {
    includeBuild("build-settings-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()

        val additionalRepositoryProperty = providers.gradleProperty("kotlin_repo_url")
        if (additionalRepositoryProperty.isPresent) {
            maven(url = uri(additionalRepositoryProperty.get()))
            logger.info("A custom Kotlin repository ${additionalRepositoryProperty.get()} was added")
        }

        /*
         * This property group is used to build kotlinx.atomicfu against Kotlin compiler snapshots.
         * When build_snapshot_train is set to true, mavenLocal and Sonatype snapshots are added to repository list
         * (the former is required for AFU and public, the latter is required for compiler snapshots).
         * DO NOT change the name of these properties without adapting kotlinx.train build chain.
         */
        val buildSnapshotTrainGradleProperty = providers.gradleProperty("build_snapshot_train")
        if (buildSnapshotTrainGradleProperty.isPresent) {
            maven(url = uri("https://oss.sonatype.org/content/repositories/snapshots"))
        }
    }
}

dependencyResolutionManagement {

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    @Suppress("UnstableApiUsage")
    repositories {

        /*
        * This property group is used to build kotlinx.atomicfu against Kotlin compiler snapshots.
        * When build_snapshot_train is set to true, mavenLocal and Sonatype snapshots are added to repository list
        * (the former is required for AFU and public, the latter is required for compiler snapshots).
        * DO NOT change the name of these properties without adapting kotlinx.train build chain.
        */
        val buildSnapshotTrainGradleProperty = providers.gradleProperty("build_snapshot_train")
        if (buildSnapshotTrainGradleProperty.isPresent) {
            maven(url = uri("https://oss.sonatype.org/content/repositories/snapshots"))
        }

        val additionalRepositoryProperty = providers.gradleProperty("kotlin_repo_url")
        if (additionalRepositoryProperty.isPresent) {
            maven(url = uri(additionalRepositoryProperty.get()))
            logger.info("A custom Kotlin repository ${additionalRepositoryProperty.get()} was added")
        }

        mavenCentral()

        // we have such a task https://youtrack.jetbrains.com/issue/KT-34732 to move these artifacts to the Maven repository,
        // but before that we need to have ivy for yarn and node dependencies
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                    content { includeModule("org.nodejs", "node") }
                }
            }
            filter { includeGroup("org.nodejs") }
        }

        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                    content { includeModule("com.yarnpkg", "yarn") }
                }
            }
            filter { includeGroup("com.yarnpkg") }
        }

    }

    versionCatalogs {
        create("libs") {

            val kotlinVersion = providers.gradleProperty("kotlin_version").orNull
            if (kotlinVersion != null) {
                version("kotlin", kotlinVersion)
            }
        }
    }
}

plugins {
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