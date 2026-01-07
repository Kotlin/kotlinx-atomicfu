/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("kotlin-jvm-publish-conventions")
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
    api(libs.bundles.asm)
    api(libs.slf4j.api)
    api(libs.mozilla.rhino)
    api(libs.kotlin.stdlib.compat)
    compileOnly(libs.kotlin.metadataJvm.compat) // will be supplied by the plugin
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.kotlin.metadataJvm.compat)
}
