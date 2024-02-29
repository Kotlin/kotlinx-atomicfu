buildscript {
    dependencies {
        classpath(libs.atomicfuGradlePlugin)
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

apply (plugin = "kotlinx-atomicfu")

kotlin {

    jvm()
    
    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
        }
    }
}
