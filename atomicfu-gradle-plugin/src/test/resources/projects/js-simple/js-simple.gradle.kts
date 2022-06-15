import kotlinx.atomicfu.plugin.gradle.*

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.17.0")
    }
}

plugins {
    kotlin("js")
}

apply(plugin = "kotlinx-atomicfu")

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation(kotlin("test-junit"))
    implementation("org.jetbrains.kotlin:kotlin-test-js")
}

kotlin {
    js {
        nodejs()
    }

    tasks.named("compileTestKotlinJs") {
        doLast {
            file("$buildDir/test_compile_js_classpath.txt").writeText(
                target.compilations["test"].compileDependencyFiles.joinToString("\n")
            )
        }
    }
}
