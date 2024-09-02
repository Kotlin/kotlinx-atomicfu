/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("kotlin-jvm-publish-conventions")
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
    api(libs.kotlin.metadataJvm)

    compileOnly(libs.kotlin.stdlib)

    runtimeOnly(libs.slf4j.simple)
}
