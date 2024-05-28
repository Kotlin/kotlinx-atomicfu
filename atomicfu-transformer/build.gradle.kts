/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("kotlin-jvm-conventions")
    id("compile-options-conventions")
}

dependencies {
    api(libs.bundles.asm)
    api(libs.slf4j.api)
    api(libs.mozilla.rhino)
    api(libs.kotlinx.metadataJvm)

    compileOnly(libs.kotlin.stdlib)

    runtimeOnly(libs.slf4j.simple)
}
