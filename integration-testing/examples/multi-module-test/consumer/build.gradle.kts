/*
 *
 *  * Copyright 2016-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 *  
 */

plugins {
    kotlin("multiplatform")
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)

    jvm {
        withJava()
    }

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

application {
    mainClass.set("MainKt")
}
