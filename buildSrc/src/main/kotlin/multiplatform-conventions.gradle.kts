import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
}

tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
}

tasks.withType<KotlinNativeCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
}