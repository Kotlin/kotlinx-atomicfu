import org.gradle.kotlin.dsl.maven

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()

        val additionalRepositoryProperty = providers.gradleProperty("kotlin_repo_url")
        if (additionalRepositoryProperty.isPresent) {
            maven(additionalRepositoryProperty.get()) {
                name = "KotlinDevRepo"
            }
            logger.info("A custom Kotlin repository ${additionalRepositoryProperty.get()} was added")
        }

        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            mavenContent { snapshotsOnly() }
        }
    }
}

dependencyResolutionManagement {

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    @Suppress("UnstableApiUsage")
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            mavenContent { snapshotsOnly() }
        }

        val additionalRepositoryProperty = providers.gradleProperty("kotlin_repo_url")
        if (additionalRepositoryProperty.isPresent) {
            maven(additionalRepositoryProperty.get()) {
                name = "KotlinDevRepo"
            }
            logger.info("A custom Kotlin repository ${additionalRepositoryProperty.get()} was added")
        }

        mavenCentral()


        // we have such a task https://youtrack.jetbrains.com/issue/KT-34732 to move these artifacts to the Maven repository,
        // but before that we need to have ivy for yarn and node dependencies
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "NodeJS repository"
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
                    name = "Yarn release repository"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                    content { includeModule("com.yarnpkg", "yarn") }
                }
            }
            filter { includeGroup("com.yarnpkg") }
        }
    }

    versionCatalogs {
        register("libs").configure {
            val kotlinVersion = providers.gradleProperty("kotlin_version").orNull
            if (kotlinVersion != null) {
                version("kotlin", kotlinVersion)
            }
        }
    }
}