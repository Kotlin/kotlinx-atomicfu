/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

buildscript {
    dependencies {
        classpath(libs.atomicfuGradlePlugin)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    mavenLocal()
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

apply (plugin = "kotlinx-atomicfu")

kotlin {

    jvm()

    js(IR)

    macosArm64()
    macosX64()
    linuxArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain{
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<KotlinCompile<*>>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xskip-prerelease-check")
    }
}
