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

group = "kotlinx.atomicfu.examples"
version = "DUMMY_VERSION"

plugins {
    kotlin("jvm") version libs.versions.kotlinVersion.get()
    `maven-publish`
}

apply(plugin = "kotlinx-atomicfu")

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("test-junit"))
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xskip-prerelease-check")
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
            artifactId = "jvm-sample"

            from(components["kotlin"])
        }
    }
}
