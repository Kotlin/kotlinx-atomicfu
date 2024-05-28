import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask

plugins {
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator)
}

val deploy by tasks.creating {
val buildSnapshotTrainGradleProperty = findProperty("build_snapshot_train")
extra["build_snapshot_train"] = buildSnapshotTrainGradleProperty != null && buildSnapshotTrainGradleProperty != ""

if (extra.has("build_snapshot_train") && extra["build_snapshot_train"] == true) {
    afterEvaluate {
        println("Manifest of kotlin-compiler-embeddable.jar for atomicfu")
        subprojects
            .filter { it.name == "atomicfu" }
            .forEach { project ->
                project.configurations.findByName("kotlinCompilerClasspath")?.let {
                    it.resolve()
                        .filter { it.name.contains("kotlin-compiler-embeddable") }
                        .forEach { file ->
                            val manifest = zipTree(file).matching {
                                include("META-INF/MANIFEST.MF")
                            }.first()

                            manifest.readText().lines().forEach { line ->
                                println(line)
                            }
                        }
                }
            }
    }
}

val deploy by tasks.creating() {
    dependsOn(getTasksByName("publish", true))
    dependsOn(getTasksByName("publishNpm", true))
}

// Right now it is used for switching nodejs version which is supports generated wasm bytecode
extensions.findByType(NodeJsRootExtension::class.java)?.let {
    // canary nodejs that supports recent Wasm GC changes
    it.nodeVersion = "21.0.0-v8-canary202309167e82ab1fa2"
    it.nodeDownloadBaseUrl = "https://nodejs.org/download/v8-canary"
}

// We need to ignore unsupported engines (i.e. canary) for npm
tasks.withType(KotlinNpmInstallTask::class).configureEach {
    args.add("--ignore-engines")
}