import org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader

buildscript {

    /*
     * These property group is used to build kotlinx-atomicfu against Kotlin compiler snapshot.
     * How does it work:
     * When build_snapshot_train is set to true, kotlin_version property is overridden with kotlin_snapshot_version,
     * atomicfu_version is overwritten by TeamCity environment (AFU is built with snapshot and published to mavenLocal
     * as previous step or the snapshot build).
     * Additionally, mavenLocal and Sonatype snapshots are added to repository list and stress tests are disabled.
     * DO NOT change the name of these properties without adapting kotlinx.train build chain.
     */
    val prop = project.properties["build_snapshot_train"]
    val build_snapshot_train = prop != null && prop != ""
    val kotlin_version = project.properties["kotlin_snapshot_version"]
    if (build_snapshot_train) {
        if (kotlin_version == null) {
            throw IllegalArgumentException("'kotlin_snapshot_version' should be defined when building with snapshot compiler")
        }
    }

    // Determine if any project dependency is using a snapshot version
    var using_snapshot_version = build_snapshot_train
    project.properties.forEach {
        if (it.key.endsWith("_version") && (it.value as String).endsWith("-SNAPSHOT")) {
            println("NOTE: USING SNAPSHOT VERSION: ${it.key}=${it.value}")
            using_snapshot_version = true
        }
    }

    if (using_snapshot_version) {
        repositories {
            mavenLocal()
            maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        }
    }
}

plugins {
    kotlin("jvm") version "${project.properties["kotlin_version"]}" // todo: is there a more consise way to get kotlin_version from gradle.properties?
}

repositories {
    mavenLocal()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("org.jetbrains.kotlin:kotlin-script-runtime")
    implementation(kotlin("script-runtime"))
}

sourceSets {
    create("mavenTest") {
        compileClasspath += files(sourceSets.main.get().output, configurations.testRuntimeClasspath)
        runtimeClasspath += output + compileClasspath

        dependencies {
            implementation("org.jetbrains.kotlinx:atomicfu-jvm:${project.properties["atomicfu_version"]}")
        }
    }

    create("functionalTest") {
        compileClasspath += files(sourceSets.main.get().output, configurations.testRuntimeClasspath)
        runtimeClasspath += output + compileClasspath

        dependencies {
            testImplementation(gradleTestKit())
            api("org.ow2.asm:asm:9.3")
            api("org.ow2.asm:asm-commons:9.3")
        }
    }
}

val mavenTest by tasks.registering(Test::class) {
    testClassesDirs = sourceSets["mavenTest"].output.classesDirs
    classpath = sourceSets["mavenTest"].runtimeClasspath
}

val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath

    systemProperties["atomicfuVersion"] = project.properties["atomicfu_version"]
}

tasks.check { dependsOn(mavenTest, functionalTest) }

// Setup K/N infrastructure to use klib utility in tests
val Project.konanHome: String
get() = project.properties["kotlin.native.home"]?.toString()
        ?: NativeCompilerDownloader(project).compilerDirectory.absolutePath

println("kotlin.native.home = ${project.konanHome}")

val embeddableJar = File(project.konanHome).resolve("konan/lib/kotlin-native-compiler-embeddable.jar")

tasks.withType<Test> {
    // Pass the path to native jars
    systemProperty("kotlin.native.jar", embeddableJar)
}