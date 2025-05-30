group = "kotlinx.atomicfu.examples"
version = "DUMMY_VERSION"

plugins {
    application
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
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

application {
    mainClass.set("org.example.MainKt")
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
