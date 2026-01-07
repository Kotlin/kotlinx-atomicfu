/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    alias(libs.plugins.gradle.pluginPublish)
    id("kotlin-jvm-conventions")
    id("java-gradle-plugin")
    id("gradle-compatibility")
}

// Gradle plugin must be compiled targeting the same Kotlin version as used by Gradle
kotlin.sourceSets.configureEach {
    languageSettings {
        languageVersion = "2.0"
        apiVersion = "2.0"
    }
}

dependencies {
    compileOnly(project(":atomicfu-transformer"))
    compileOnly(gradleApi())
    compileOnly(libs.kotlin.stdlib.compat)
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
    val projectVersion = project.version
    inputs.property("atomicfuVersion", projectVersion)
    filesMatching("atomicfu.properties") {
        expand("atomicfuVersion" to projectVersion)
    }
}

signing {
    // disable signing if private key isn't passed
    isRequired = findProperty("libs.sign.key.private") != null
}

gradlePlugin {
    website.set("https://github.com/Kotlin/kotlinx-atomicfu")
    vcsUrl.set("https://github.com/Kotlin/kotlinx-atomicfu.git")

    plugins {
        create("Atomicfu") {
            id = "org.jetbrains.kotlinx.atomicfu"
            implementationClass = "kotlinx.atomicfu.plugin.gradle.AtomicFUGradlePlugin"
            displayName = "Gradle plugin for kotlinx-atomicfu library"
            description = "Enables efficient use of atomic operations in Kotlin multiplatform projects."
            tags = setOf("kotlinx-atomicfu", "atomics", "kotlin")
        }
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
}
