import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
//     Regular java modules need 'java-library' plugin for proper publication
    `java-library`
    kotlin("jvm")
    id("base-publish-conventions")
    id("kotlin-base-conventions")
}

kotlin {
    jvmToolchain(8)

    compilerOptions {
        freeCompilerArgs.add("-Xallow-pre-17-runtime-jdk")
    }
}

project.configureImplementationJarManifest("jar")
