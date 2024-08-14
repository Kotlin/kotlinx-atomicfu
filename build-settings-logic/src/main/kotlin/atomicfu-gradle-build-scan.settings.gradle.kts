// this is a settings convention plugin for Gradle Develocity

plugins {
    id("com.gradle.develocity")
}

develocity {
    if (buildScanEnabled.get()) {
        val overriddenName = buildScanUsername.orNull
        server = "https://ge.jetbrains.com/"
        buildScan {
            publishing.onlyIf { true }
            capture {
                fileFingerprints = true
                buildLogging = true
                uploadInBackground = true
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
