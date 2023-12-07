pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("kotlinVersion", providers.gradleProperty("kotlin_version").orNull)
        }
    }
}

rootProject.name = "user-project"
