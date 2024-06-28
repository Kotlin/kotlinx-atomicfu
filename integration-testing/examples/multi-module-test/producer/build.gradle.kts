plugins {
    kotlin("multiplatform") version libs.versions.kotlinVersion.get()
    id("org.jetbrains.kotlinx.atomicfu") version libs.versions.atomicfuVersion.get()
}

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
    }
}
