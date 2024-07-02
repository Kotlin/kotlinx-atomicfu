rootProject.name = "build-settings-logic"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

//apply(from = "src/main/kotlin/gradle-build-cache.settings.gradle.kts")
