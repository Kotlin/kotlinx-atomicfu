dependencyResolutionManagement {

    @Suppress("UnstableApiUsage")
    repositories {
        val buildSnapshotTrainGradleProperty = providers.gradleProperty("build_snapshot_train")
        if (buildSnapshotTrainGradleProperty.isPresent) {
            maven(url = uri("https://oss.sonatype.org/content/repositories/snapshots"))
        }

        val additionalRepositoryProperty = providers.gradleProperty("community.project.kotlin.repo")
            .orElse(providers.gradleProperty("kotlin_repo_url"))
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

            val overridingKotlinVersion =  providers.gradleProperty("community.project.kotlin.version").orNull
            if (overridingKotlinVersion != null) {
                logger.info("An overriding Kotlin version of $overridingKotlinVersion was found for buildSrc")
                version("kotlin", overridingKotlinVersion)
            }

            /*
            * This property group is used to build kotlinx.atomicfu against Kotlin compiler snapshots.
            * When build_snapshot_train is set to true, kotlin_version property is overridden with kotlin_snapshot_version.
            * Additionally, mavenLocal and Sonatype snapshots are added to repository list
            * (the former is required for AFU and public, the latter is required for compiler snapshots).
            * DO NOT change the name of these properties without adapting kotlinx.train build chain.
            */
            val buildSnapshotTrainGradleProperty = providers.gradleProperty("build_snapshot_train").orNull
            if (buildSnapshotTrainGradleProperty != null && buildSnapshotTrainGradleProperty != "") {
                val kotlinVersion = providers.gradleProperty("kotlin_snapshot_version").orNull
                    ?: throw IllegalArgumentException("'kotlin_snapshot_version' should be defined when building with a snapshot compiler")
                version("kotlin", kotlinVersion)
            }
        }
    }
}