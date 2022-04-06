import org.jetbrains.kotlin.gradle.plugin.*

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.17.0")
    }
}

plugins {
    kotlin("multiplatform")
}

apply(plugin = "kotlinx-atomicfu")

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
}

kotlin {
    targets {
        jvm {
            compilations.all {
                kotlinOptions.jvmTarget = "1.8"
            }
            testRuns["test"].executionTask.configure {
                useJUnit()
            }
        }
        js {
            nodejs()
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-js")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-js")
            }
        }
    }

    tasks.named("compileTestKotlinJvm") {
        doLast {
            file("$buildDir/test_compile_jvm_classpath.txt").writeText(
                targets["jvm"].compilations["test"].compileDependencyFiles.joinToString("\n")
            )
        }
    }

    tasks.named("jvmTest") {
        doLast {
            file("$buildDir/test_runtime_jvm_classpath.txt").writeText(
                (targets["jvm"].compilations["test"] as KotlinCompilationToRunnableFiles).runtimeDependencyFiles.joinToString("\n")
            )
        }
    }

    tasks.named("compileTestKotlinJs") {
        doLast {
            file("$buildDir/test_compile_js_classpath.txt").writeText(
                targets["js"].compilations["test"].compileDependencyFiles.joinToString("\n")
            )
        }
    }
}
