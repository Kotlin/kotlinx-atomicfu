plugins {
    kotlin("multiplatform")
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

// Workaround for KT-71203. Can be removed after https://github.com/Kotlin/kotlinx-atomicfu/issues/431
atomicfu {
    transformJs = false
}
