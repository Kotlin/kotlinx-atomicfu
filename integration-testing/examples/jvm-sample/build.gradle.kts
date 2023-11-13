buildscript {
    repositories {
        mavenLocal()
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${libs.versions.atomicfuVersion.get()}")
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
