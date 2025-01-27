rootProject.name = "build-settings-logic"

dependencyResolutionManagement {

    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

apply(from = "src/main/kotlin/atomicfu-cache-redirector.settings.gradle.kts")

/*
    We have to make sure the build-cache config is consistent in the settings.gradle.kts
    files of all projects. The natural way to share logic is with a convention plugin,
    but we can't apply a convention plugin in build-settings-logic to itself, so we just copy it.
    We could publish build-settings-logic to a Maven repo? But this is quicker and easier.
    The following content should be kept in sync with the content of:
    `src/main/kotlin/atomicfu-develocity.settings.gradle.kts`
    The only difference with the script above is explicitly specified versions
 */

// version should be kept in sync with `gradle/libs.versions.toml`
plugins {
    id("com.gradle.develocity") version "3.17.6"
}

val buildingOnTeamCity: Boolean = System.getenv("TEAMCITY_VERSION") != null
val buildingOnGitHub: Boolean = System.getenv("GITHUB_ACTION") != null
val buildingOnCi: Boolean = System.getenv("CI") != null || buildingOnTeamCity || buildingOnGitHub

// NOTE: build scan properties are documented in README.md
val Settings.buildScanEnabled: Provider<Boolean>
    get() =
        atomicfuProperty("build.scan.enabled", String::toBoolean)
            .orElse(buildingOnCi)

val DEFAULT_ATOMICFU_USER_NAME = "<default>"

/**
 * Optionally override the default name attached to a Build Scan.
 */
val Settings.buildScanUsername: Provider<String>
    get() =
        atomicfuProperty("build.scan.username")
            .orElse(DEFAULT_ATOMICFU_USER_NAME)
            .map(String::trim)

/**
 * Disable Local Cache on CI, because CI machines are short-lived, so local caching doesn't help a lot.
 * Also, to force CI machines to update the remote cache.
 */
val Settings.buildCacheLocalEnabled: Provider<Boolean>
    get() = atomicfuProperty("build.cache.local.enabled", String::toBoolean)
        .orElse(!buildingOnCi)

val Settings.buildCacheLocalDirectory: Provider<String>
    get() = atomicfuProperty("build.cache.local.directory")

val Settings.buildCachePushEnabled: Provider<Boolean>
    get() = atomicfuProperty("build.cache.push", String::toBoolean)
        .orElse(buildingOnTeamCity)

internal fun Settings.atomicfuProperty(name: String): Provider<String> =
    providers.gradleProperty("org.jetbrains.atomicfu.$name")

internal fun <T : Any> Settings.atomicfuProperty(name: String, convert: (String) -> T): Provider<T> =
    atomicfuProperty(name).map(convert)

develocity {
    val buildScanEnabled = buildScanEnabled.get()
    server = "https://ge.jetbrains.com/"
    if (buildScanEnabled) {
        val overriddenName = buildScanUsername.orNull
        buildScan {
            publishing.onlyIf { buildScanEnabled }
            capture {
                fileFingerprints = buildScanEnabled
                buildLogging = buildScanEnabled
                uploadInBackground = buildScanEnabled
            }
            obfuscation {
                ipAddresses { _ -> listOf("0.0.0.0") }
                hostname { _ -> "concealed" }
                username { originalUsername ->
                    when {
                        buildingOnTeamCity -> "TeamCity"
                        buildingOnGitHub -> "GitHub"
                        buildingOnCi -> "CI"
                        !overriddenName.isNullOrBlank() && overriddenName != DEFAULT_ATOMICFU_USER_NAME -> overriddenName
                        overriddenName == DEFAULT_ATOMICFU_USER_NAME -> originalUsername
                        else -> "unknown"
                    }
                }
            }
        }
    }
}

buildCache {
    local {
        isEnabled = buildCacheLocalEnabled.get()
        if (buildCacheLocalDirectory.orNull != null) {
            directory = buildCacheLocalDirectory.get()
        }
    }
    remote(develocity.buildCache) {
        isPush = buildCachePushEnabled.get()
    }
}