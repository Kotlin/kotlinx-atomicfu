import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

buildscript {
    dependencies {
        classpath(libs.atomicfuGradlePlugin)
    }
}

repositories {
    mavenCentral()
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
                implementation(kotlin("test-junit"))
            }
        }
    }
}

tasks.withType<KotlinCompile<*>>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xskip-prerelease-check")
    }
}
