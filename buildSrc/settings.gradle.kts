rootProject.name = "buildSrc"

pluginManagement {
    includeBuild("../build-settings-logic")
}

dependencyResolutionManagement {

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
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))

            val kotlinVersion = providers.gradleProperty("kotlin_version").orNull
            if (kotlinVersion != null) {
                version("kotlin", kotlinVersion)
            }
        }
    }
}

plugins {
    id("atomicfu-cache-redirector")
}