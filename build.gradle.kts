@file:OptIn(ExperimentalBCVApi::class)

import kotlinx.validation.ExperimentalBCVApi

plugins {
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator)
}

val deploy by tasks.registering {
    dependsOn(
        getTasksByName("publish", true),
        getTasksByName("publishNpm", true)
    )
}

// This is a WA for the failure of the :model task during publication:
// "Task `:model` of type `org.gradle.api.reporting.model.ModelReport`: invocation of 'Task.project' at execution time is unsupported."
// model task is incompatible with configuration cache, and this WA makes a warning from this error.
// Also, model tasks are going to be completely removed: https://github.com/gradle/gradle/issues/15023
tasks.named("model") {
    notCompatibleWithConfigurationCache("uses Task.project")
}

apiValidation {
    klib.enabled = true
}
