plugins {
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator)
}

val deploy: Task? by tasks.creating {
    dependsOn(getTasksByName("publish", true))
    dependsOn(getTasksByName("publishNpm", true))
}