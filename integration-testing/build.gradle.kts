/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader

plugins {
    id("kotlin-jvm-conventions")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

kotlin {
    jvmToolchain(11)
}

sourceSets {
    create("mavenTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output

        configurations.getByName(implementationConfigurationName) {
            extendsFrom(configurations.getByName(sourceSets.main.get().implementationConfigurationName))
            extendsFrom(configurations.getByName(sourceSets.test.get().implementationConfigurationName))
        }
    }

    create("functionalTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output

        configurations.getByName(implementationConfigurationName) {
            extendsFrom(configurations.getByName(sourceSets.main.get().implementationConfigurationName))
            extendsFrom(configurations.getByName(sourceSets.test.get().implementationConfigurationName))
        }

        configurations.getByName(runtimeOnlyConfigurationName) {
            extendsFrom(configurations.getByName(sourceSets.main.get().runtimeOnlyConfigurationName))
            extendsFrom(configurations.getByName(sourceSets.test.get().runtimeOnlyConfigurationName))
        }

    }
}

dependencies {
    // common dependencies
    implementation(libs.kotlin.stdlibJdk8)
    testImplementation(libs.kotlin.test)
    implementation(libs.kotlin.scriptRuntime)

    // mavenTest dependencies
    "mavenTestImplementation"(project(":atomicfu"))

    // functionalTest dependencies
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestApi"(libs.asm)
    "functionalTestApi"(libs.asm.commons)
}

val mavenTest by tasks.registering(Test::class) {
    testClassesDirs = sourceSets["mavenTest"].output.classesDirs
    classpath = sourceSets["mavenTest"].runtimeClasspath

    dependsOn(":atomicfu:publishToMavenLocal")

    outputs.upToDateWhen { false }
}

val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath

    // the kotlin version used to build the library, which is set in root gradle.properties or overriden by the TC config
    systemProperties["kotlin.version.integration"] = providers.gradleProperty("kotlin_version").orNull
    // the kotlin version used to build the library, which is set in root gradle.properties or overriden by the TC config
    systemProperties["kotlin.native.version.integration"] = providers.gradleProperty("kotlin.native.version").orNull
    // the current atomicfu version set in the root gradle.properties
    systemProperties["atomicfu.snapshot.version.integration"] = providers.gradleProperty("version").orNull
    // the directory (on TC agent) where Kotlin artifacts were published during the Aggregate build
    systemProperties["kotlin.artifacts.repository.integration"] = providers.gradleProperty("kotlin_repo_url").orNull

    dependsOn(":atomicfu-gradle-plugin:publishToMavenLocal")
    // atomicfu-transformer and atomicfu artifacts should also be published as it's required by atomicfu-gradle-plugin.
    dependsOn(":atomicfu-transformer:publishToMavenLocal")
    dependsOn(":atomicfu:publishToMavenLocal")

    outputs.upToDateWhen { false }
}

tasks.check { dependsOn(mavenTest, functionalTest) }

// Setup K/N infrastructure to use klib utility in tests
// TODO: klib checks are skipped for now because of this problem KT-61143
val Project.konanHome: String
    get() = rootProject.properties["kotlin.native.home"]?.toString()
        ?: NativeCompilerDownloader(project).compilerDirectory.absolutePath

tasks.withType<Test> {
    val embeddableJar = File(project.konanHome).resolve("konan/lib/kotlin-native-compiler-embeddable.jar")
    // Pass the path to native jars
    systemProperty("kotlin.native.jar", embeddableJar)

    // Pass the path to the cache redirector
    val cacheRedirectorPath = project.file("../build-settings-logic/src/main/kotlin/atomicfu-cache-redirector.settings.gradle.kts")
    systemProperty("cache.redirector.path", cacheRedirectorPath.absolutePath)

    val forks = project.providers.gradleProperty("testing.max.forks").orNull?.toInt()
        ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    maxParallelForks = forks
}
