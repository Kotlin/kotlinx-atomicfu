buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    
    dependencies {
        val atomicfuVersion = libs.versions.atomicfuVersion.get()
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfuVersion")
    }
}

plugins {
    kotlin("multiplatform") version libs.versions.kotlinVersion.get()
}

apply(plugin = "kotlinx-atomicfu")

repositories {
    mavenLocal()
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

        // This is necessary for now, due to the problem with dependency configuration, see #403
        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:atomicfu:0.23.2")
            }
        }
    }
}
