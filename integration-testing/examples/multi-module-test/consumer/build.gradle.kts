/*
 *
 *  * Copyright 2016-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 *  
 */

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)

    jvm()

    sourceSets {
        all {
            languageSettings.apply {
                languageVersion = "2.0"
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(project(":producer"))
            }
        }
    }
}

