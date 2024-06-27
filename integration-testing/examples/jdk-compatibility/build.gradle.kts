import org.jetbrains.kotlin.config.JvmTarget

group = "kotlinx.atomicfu.examples"
version = "DUMMY_VERSION"

plugins {
    kotlin("jvm") version libs.versions.kotlinVersion.get()
    id("org.jetbrains.kotlinx.atomicfu") version libs.versions.atomicfuVersion.get()
    `maven-publish`
}

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

kotlin {
    val minTarget = JvmTarget.supportedValues().minBy { it.majorVersion }
    val maxTarget = JvmTarget.supportedValues().maxBy { it.majorVersion }

    val useMax = (project.properties["useMaxVersion"]?.toString() ?: "false").toBoolean()
    val target = (if (useMax) maxTarget else minTarget).toString()

    val toolchainVersion = target.split('.').last().toInt()
    val targetVersionString = "JVM_" + target.replace('.', '_')
    jvmToolchain(toolchainVersion)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.valueOf(targetVersionString))
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
            artifactId = "jdk-compatibility"

            from(components["kotlin"])
        }
    }
}
