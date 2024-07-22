import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
    id("kotlin-base-conventions")
}

tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
}

tasks.withType<KotlinNativeCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
}

tasks.withType<KotlinCompile>().configureEach {
    // Suppress the warning: 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta.
    // See: https://youtrack.jetbrains.com/issue/KT-61573
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}