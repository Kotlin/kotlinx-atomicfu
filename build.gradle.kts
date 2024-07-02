import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask

plugins {
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator)
}

val deploy: Task? by tasks.creating {
    dependsOn(getTasksByName("publish", true))
    dependsOn(getTasksByName("publishNpm", true))
}

// We need to ignore unsupported engines (i.e. canary) for npm
tasks.withType(KotlinNpmInstallTask::class).configureEach {
    args.add("--ignore-engines")
}