/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

buildscript {
    val atomicfu_version = rootProject.properties["atomicfu_version"]

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version")
    }
}

group = "kotlinx.atomicfu.examples"
version = "DUMMY_VERSION"

plugins {
    kotlin("multiplatform") version "${project.properties["kotlin_version"]}"
    `maven-publish`
}

apply(plugin = "kotlinx-atomicfu")

repositories {
    mavenLocal()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    mavenCentral()
}

kotlin {
    jvm()
    //js()

    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
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