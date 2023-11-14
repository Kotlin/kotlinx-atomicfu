/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${libs.versions.atomicfuVersion.get()}")
    }
}

group = "kotlinx.atomicfu.examples"
version = "DUMMY_VERSION"

plugins {
    kotlin("multiplatform") version libs.versions.kotlinVersion.get()
    `maven-publish`
}

apply(plugin = "kotlinx-atomicfu")

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
                implementation(kotlin("test-junit"))
            }
        }
        commonTest {}
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
