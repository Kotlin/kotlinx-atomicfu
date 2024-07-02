import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask

plugins {
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator)
}

val deploy: Task? by tasks.creating {
    dependsOn(getTasksByName("publish", true))
    dependsOn(getTasksByName("publishNpm", true))
}

// Right now it is used for switching nodejs version which is supports generated wasm bytecode - Remove after updating to Kotlin 2.0
extensions.findByType(NodeJsRootExtension::class.java)?.let {
    // canary nodejs that supports recent Wasm GC changes
    it.nodeVersion = "21.0.0-v8-canary202309167e82ab1fa2"
    it.nodeDownloadBaseUrl = "https://nodejs.org/download/v8-canary"
}

// We need to ignore unsupported engines (i.e. canary) for npm
tasks.withType(KotlinNpmInstallTask::class).configureEach {
    args.add("--ignore-engines")
}