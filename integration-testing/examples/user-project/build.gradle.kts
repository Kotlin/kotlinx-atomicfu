/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

plugins {
    kotlin("multiplatform") version libs.versions.kotlinVersion.get()
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    val localRepositoryUrl = findProperty("localRepositoryUrl")
    if (localRepositoryUrl != null) {
        maven(localRepositoryUrl)   
    }
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
                implementation(kotlin("test-junit"))
                implementation("kotlinx.atomicfu.examples:mpp-sample:DUMMY_VERSION")
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

// Workaround for https://youtrack.jetbrains.com/issue/KT-58303:
// the `clean` task can't delete the expanded.lock file on Windows as it's still held by Gradle, failing the build
tasks.clean {
    setDelete(layout.buildDirectory.asFileTree.matching {
        exclude("tmp/.cache/expanded/expanded.lock")
    })
}
