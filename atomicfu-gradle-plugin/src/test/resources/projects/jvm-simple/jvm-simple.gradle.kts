import org.gradle.api.tasks.compile.*
import org.jetbrains.kotlin.gradle.plugin.*

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.17.0")
    }
}

plugins {
    kotlin("jvm")
}

apply(plugin = "kotlinx-atomicfu")

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("test-junit"))
}

kotlin {
    tasks.compileTestKotlin {
        doLast {
            file("$buildDir/test_compile_jvm_classpath.txt").writeText(
                target.compilations["test"].compileDependencyFiles.joinToString("\n")
            )
        }
    }

    tasks.test {
        doLast {
            file("$buildDir/test_runtime_jvm_classpath.txt").writeText(
                (target.compilations["test"] as KotlinCompilationToRunnableFiles<*>).runtimeDependencyFiles.joinToString("\n")
            )
        }
    }
}
