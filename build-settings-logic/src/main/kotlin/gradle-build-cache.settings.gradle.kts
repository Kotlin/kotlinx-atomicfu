/**
 * Gradle Build Cache conventions.
 *
 * Because Atomicfu uses Composite Builds, Build Cache must be configured consistently on
 * - the root settings.gradle.kts,
 * - and the settings.gradle.kts of any projects added with `pluginManagement { includedBuild("...") }`
 *
 * See https://docs.gradle.org/8.8/userguide/build_cache.html#sec:build_cache_composite
 *
 * ⚠️ This file _must_ be applicable as a script plugin and so _must not_ depend on other source files.
 *
 * Based on https://github.com/JetBrains/kotlin/blob/2675531624d42851af502a993bbefd65ee3e38ef/repo/gradle-settings-conventions/build-cache/src/main/kotlin/build-cache.settings.gradle.kts
 */

// Don't remove or move to *.kt files these functions and variables
// they are required for applying this plugin as convention plugin to `build-settings-logic` settings file
internal fun Settings.atomicfuProperty(name: String): Provider<String> =
    providers.gradleProperty("org.jetbrains.atomicfu.$name")

internal fun <T : Any> Settings.atomicfuProperty(name: String, convert: (String) -> T): Provider<T> =
    atomicfuProperty(name).map(convert)

val buildingOnTeamCity: Boolean = System.getenv("TEAMCITY_VERSION") != null
val buildingOnGitHub: Boolean = System.getenv("GITHUB_ACTION") != null
val buildingOnCi: Boolean = System.getenv("CI") != null || buildingOnTeamCity || buildingOnGitHub

/**
 * Disable Local Cache on CI, because CI machines are short-lived, so local caching doesn't help a lot.
 * Also, to force CI machines to update the remote cache.
 */
val buildCacheLocalEnabled: Provider<Boolean> =
    atomicfuProperty("build.cache.local.enabled", String::toBoolean)
        .orElse(!buildingOnCi)

val buildCacheLocalDirectory: Provider<String> =
    atomicfuProperty("build.cache.local.directory")

val buildCachePushEnabled: Provider<Boolean> =
    atomicfuProperty("build.cache.push", String::toBoolean)
        .orElse(buildingOnTeamCity)

val buildCacheUser: Provider<String> =
    providers.gradleProperty("build.cache.user")

val buildCachePassword: Provider<String> =
    providers.gradleProperty("build.cache.password")

buildCache {
    local {
        isEnabled = buildCacheLocalEnabled.get()
        if (buildCacheLocalDirectory.orNull != null) {
            directory = buildCacheLocalDirectory.get()
        }
    }
    remote<HttpBuildCache> {
        url = uri("https://ge.jetbrains.com/cache/")
        isPush = buildCachePushEnabled.get()

        if (buildCacheUser.isPresent &&
            buildCachePassword.isPresent
        ) {
            credentials.username = buildCacheUser.get()
            credentials.password = buildCachePassword.get()
        }
    }
}