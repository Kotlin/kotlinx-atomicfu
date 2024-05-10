/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("java-library-conventions")
    id("java-gradle-plugin")
}

// Gradle plugin must be compiled targeting the same Kotlin version as used by Gradle
kotlin.sourceSets.configureEach {
    languageSettings {
        languageVersion = getOverridingKotlinLanguageVersion(project) ?: "1.4"
        apiVersion = getOverridingKotlinApiVersion(project) ?: "1.4"
    }
}

dependencies {
    implementation(project(":atomicfu-transformer")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    compileOnly(gradleApi())
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlin.gradlePlugin)
    // Atomicfu compiler plugin dependency will be loaded to kotlinCompilerPluginClasspath
    // Atomicfu plugin will only be applied if the flag is set kotlinx.atomicfu.enableJsIrTransformation=true
    compileOnly(libs.kotlin.atomicfu)

    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit.junit)
}

evaluationDependsOn(":atomicfu")

tasks.processResources {
    filesMatching("atomicfu.properties") {
        expand("atomicfuVersion" to project.version)
    }
}
