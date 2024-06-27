/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

group = "kotlinx.atomicfu.examples"
version = "DUMMY_VERSION"

plugins {
    kotlin("multiplatform") version libs.versions.kotlinVersion.get()
    `maven-publish`
    id("org.jetbrains.kotlinx.atomicfu") version libs.versions.atomicfuVersion.get()
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    mavenLocal()
}

kotlin {
    jvm()

    js()

    wasmJs {}
    wasmWasi {}

    macosArm64()
    macosX64()
    linuxArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("test"))
            }
        }
        commonTest {}
    }
}

tasks.withType<KotlinCompile<*>>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xskip-prerelease-check")
    }
}

publishing {
    repositories {
        /**
         * Maven repository in build directory to store artifacts for using in functional tests.
         */
        maven("build/.m2/") {
            name = "local"
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = "kotlinx.atomicfu.examples"
            artifactId = "mpp-sample"

            from(components["kotlin"])
        }
    }
}
