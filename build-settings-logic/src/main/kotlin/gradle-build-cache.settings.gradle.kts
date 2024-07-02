//region Gradle Build Cache
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
//endregion

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