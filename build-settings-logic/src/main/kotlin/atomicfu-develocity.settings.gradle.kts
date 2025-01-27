// this is a settings convention plugin for Gradle Develocity

// Based on https://github.com/JetBrains/kotlin/blob/c20f644ee4cd8d28b39b12ea5304b68c5639e531/repo/gradle-settings-conventions/develocity/src/main/kotlin/develocity.settings.gradle.kts
// Because Atomicfu uses Composite Builds, Build Cache must be configured consistently on:
// - the root settings.gradle.kts,
// - and the settings.gradle.kts of any projects added with `pluginManagement { includedBuild("...") }`
// The Content of this file should be kept in sync with the content at the end of:
//   `build-settings-logic/settings.gradle.kts`
// useful links:
// - develocity: https://docs.gradle.com/develocity/gradle-plugin/
// - build cache: https://docs.gradle.org/8.4/userguide/build_cache.html#sec:build_cache_composite
plugins {
    id("com.gradle.develocity")
}

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