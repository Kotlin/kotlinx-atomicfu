buildscript {
    val atomicfu_version = rootProject.properties["atomicfu_version"]

    repositories {
        mavenLocal()
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version")
    }
}

group = "kotlinx.atomicfu.examples"
version = "DUMMY_VERSION"

plugins {
    kotlin("jvm") version "${project.properties["kotlin_version"]}"
    `maven-publish`
}

apply(plugin = "kotlinx-atomicfu")

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
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
