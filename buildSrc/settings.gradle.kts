rootProject.name = "buildSrc"

pluginManagement {
    includeBuild("../build-settings-logic")
}

// For buildSrc we need to declare a custom path to toml file with versions' catalog.
// But for a root project we can't set `from` inside `versionCatalogs` catalog block for the default `libs` catalog.
// (see https://github.com/gradle/gradle/issues/21328)
// That is why it is not fully moved to the dependencyResolutionManagement block in the settings convention plugin.
dependencyResolutionManagement {
    versionCatalogs {
        getByName("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("atomicfu-dependency-resolution-management")
    id("atomicfu-cache-redirector")
}