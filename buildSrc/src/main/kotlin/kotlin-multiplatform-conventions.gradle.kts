import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
    id("kotlin-base-conventions")
}

tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
}

tasks.withType<KotlinNativeCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Suppress the warning: 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta.
        // See: https://youtrack.jetbrains.com/issue/KT-61573
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // Suppress "Pre-release classes were found in dependencies" error
        // to be able to use develop branch of the library in Aggregate builds with Kotlin master.
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        addKotlinUserProjectFlags()
        addExtraCompilerFlags(project)
        irValidationMode(project)?.let {
            freeCompilerArgs.addAll("-Xverify-ir=$it", "-Xverify-ir-visibility")
        }
    }
}
