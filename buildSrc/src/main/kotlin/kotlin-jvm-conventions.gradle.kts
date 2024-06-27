plugins {
//     Regular java modules need 'java-library' plugin for proper publication
    `java-library`
    kotlin("jvm")
    id("base-publish-conventions")
    id("common-conventions")
    id("kotlin-base-conventions")
}

kotlin {
    jvmToolchain(8)
}