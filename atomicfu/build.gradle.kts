import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("kotlin-multiplatform-conventions")
    id("kotlin-multiplatform-publish-conventions")
}

kotlin {
    jvmToolchain(8)
    
    // Tier 1
    macosArm64()
    iosSimulatorArm64()

    // Tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosArm64()
    iosArm64()

    // Tier 3
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    mingwX64()
    watchosDeviceArm64()

    // Deprecated
    @Suppress("DEPRECATION") //https://github.com/Kotlin/kotlinx-atomicfu/issues/207
    linuxArm32Hfp()

    @Suppress("DEPRECATION")
    iosX64()
    @Suppress("DEPRECATION")
    macosX64()
    @Suppress("DEPRECATION")
    tvosX64()
    @Suppress("DEPRECATION")
    watchosX64()

    // JS -- always
    js(IR) {
        outputModuleName = "kotlinx-atomicfu"
        // TODO: commented out because browser tests do not work on TeamCity
        // browser()
        nodejs()
    }

    // JVM -- always
    jvm()

    // Wasm -- always
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jsAndWasmShared") {
                withJs()
                withWasmJs()
                withWasmWasi()
            }
            group("concurrent") {
                withJvm()
                group("native") {
                    group("nativeUnixLike") {
                        group("apple") {
                            withApple()
                        }
                        group("linux") {
                            withLinux()
                        }
                    }
                    withMingwX64()
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib") {
                version {
                    prefer(libs.versions.kotlin.asProvider().get())
                }
            }
        }
        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test-common")
            implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
        }
        
        jsMain {
            dependencies {
                compileOnly("org.jetbrains.kotlin:kotlin-dom-api-compat")
            }
        }

        jsTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-js")
            }
        }

        wasmJsTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-wasm-js")
            }
        }

        wasmWasiTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-wasm-wasi")
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.jetbrains.lincheck)
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("org.jetbrains.kotlin:kotlin-test-junit")
                implementation(libs.junit.junit)
            }
        }
    }

    // atomicfu-cinterop-interop.klib with an empty interop.def file will still be published for compatibility reasons (see KT-68411)
    // This block can be removed when this issue in K/N compiler is resolved: KT-60874
    targets.withType(KotlinNativeTarget::class).configureEach {
        compilations.configureEach {
            cinterops {
                val interop by creating {
                    definitionFile.set(project.file("src/nativeInterop/cinterop/interop.def"))
                }
            }
        }
    }

    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xdont-warn-on-error-suppression")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }
}

val transformer: Configuration by configurations.creating

dependencies {
    val cfg = transformer
    cfg(project(":atomicfu-transformer"))
    cfg(libs.kotlin.metadataJvm)
}

// ==== CONFIGURE JVM =====

val classesPreAtomicFuDir = file("${layout.buildDirectory.get()}/classes/kotlin/jvm/test")
val classesPostTransformFU = file("${layout.buildDirectory.get()}/classes/kotlin/jvm/postTransformedFU")
val classesPostTransformVH = file("${layout.buildDirectory.get()}/classes/kotlin/jvm/postTransformedVH")
val classesPostTransformBOTH = file("${layout.buildDirectory.get()}/classes/kotlin/jvm/postTransformedBOTH")

// JDK version used to run transform JavaExec tasks and transformer tests.
private val LAUNCHER_JDK_VERSION: Int = 11

val transformFU by tasks.registering(JavaExec::class) {
    dependsOn(tasks.get("compileTestKotlinJvm"))
    mainClass = "kotlinx.atomicfu.transformer.AtomicFUTransformerKt"
    args(classesPreAtomicFuDir, classesPostTransformFU, "FU")
    classpath(transformer)
    inputs.dir(classesPreAtomicFuDir)
    outputs.dir(classesPostTransformFU)
    launcherForJdk(LAUNCHER_JDK_VERSION)
}

val transformBOTH by tasks.registering(JavaExec::class) {
    dependsOn("compileTestKotlinJvm")
    mainClass = "kotlinx.atomicfu.transformer.AtomicFUTransformerKt"
    args(classesPreAtomicFuDir, classesPostTransformBOTH, "BOTH")
    classpath = transformer
    inputs.dir(classesPreAtomicFuDir)
    outputs.dir(classesPostTransformBOTH)
    launcherForJdk(LAUNCHER_JDK_VERSION)
}


val transformVH by tasks.registering(JavaExec::class) {
    dependsOn("compileTestKotlinJvm")
    mainClass = "kotlinx.atomicfu.transformer.AtomicFUTransformerKt"
    args(classesPreAtomicFuDir, classesPostTransformVH, "VH")
    classpath(transformer)
    inputs.dir(classesPreAtomicFuDir)
    outputs.dir(classesPostTransformVH)
    launcherForJdk(LAUNCHER_JDK_VERSION)
}


val transformedTestFU_current by tasks.registering(Test::class) {
    dependsOn(transformFU)
    classpath = files(configurations.getByName("jvmTestRuntimeClasspath"), classesPostTransformFU)
    testClassesDirs = project.files(classesPostTransformFU)
    exclude(
        "**/*LFTest.*",
        "**/TraceToStringTest.*",
        "**/AtomicfuReferenceJsTest.*",
        "**/*LincheckTest.*",
    )
    filter { isFailOnNoMatchingTests = false }
    launcherForJdk(LAUNCHER_JDK_VERSION)
}

val transformedTestBOTH_current by tasks.registering(Test::class) {
    dependsOn(transformBOTH)
    classpath = files(configurations.getByName("jvmTestRuntimeClasspath"), classesPostTransformBOTH)
    testClassesDirs = project.files(classesPostTransformBOTH)
    exclude(
        "**/*LFTest.*",
        "**/TraceToStringTest.*",
        "**/TopLevelGeneratedDeclarationsReflectionTest.*",
        "**/SyntheticFUFieldsTest.*",
        "**/AtomicfuReferenceJsTest.*",
        "**/*LincheckTest.*",
    )
    filter { isFailOnNoMatchingTests = false }
    launcherForJdk(LAUNCHER_JDK_VERSION)
}

val transformedTestVH by tasks.registering(Test::class) {
    dependsOn(transformVH)
    classpath = files(configurations.getByName("jvmTestRuntimeClasspath"), classesPostTransformVH)
    testClassesDirs = project.files(classesPostTransformVH)
    exclude(
        "**/*LFTest.*",
        "**/TraceToStringTest.*",
        "**/TopLevelGeneratedDeclarationsReflectionTest.*",
        "**/SyntheticFUFieldsTest.*",
        "**/AtomicfuReferenceJsTest.*",
        "**/*LincheckTest.*",
    )
    filter { isFailOnNoMatchingTests = false }

    onlyIf { currentTask ->
        logger.info("Current java version for task ${currentTask.name} is : ${JavaVersion.current()}")
        JavaVersion.current().ordinal >= JavaVersion.VERSION_1_9.ordinal
    }
    launcherForJdk(LAUNCHER_JDK_VERSION)
}
val jvmTestAll by tasks.registering {
    dependsOn(
        transformedTestFU_current,
        transformedTestBOTH_current,
        transformedTestVH
    )
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
    }
}

val compileJavaModuleInfo by tasks.registering(JavaCompile::class) {
    val moduleName = "kotlinx.atomicfu" // this module's name
    val compilation = kotlin.targets["jvm"].compilations["main"]
    val compileKotlinTask = compilation.compileTaskProvider.get() as KotlinJvmCompile
    val targetDir = compileKotlinTask.destinationDirectory.dir("../java9")
    val sourceDir = file("src/jvmMain/java9/")

    // Use a Java 11 compiler for the module info.
    javaCompiler.set(project.javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(11)) })

    // Always compile kotlin classes before the module descriptor.
    dependsOn(compileKotlinTask)

    // Add the module-info source file.
    source(sourceDir)

    // Also add the module-info.java source file to the Kotlin compile task.
    // The Kotlin compiler will parse and check module dependencies,
    // but it currently won't compile to a module-info.class file.
    // Note that module checking only works on JDK 9+,
    // because the JDK built-in base modules are not available in earlier versions.
    val javaVersion = compileKotlinTask.kotlinJavaToolchain.javaVersion.getOrNull()
    when {
        javaVersion?.isJava9Compatible == true -> {
            logger.info("Module-info checking is enabled; $compileKotlinTask is compiled using Java $javaVersion")
            compileKotlinTask.source(sourceDir)
        }

        else -> {
            logger.info("Module-info checking is disabled")
        }
    }
    // Set the task outputs and destination dir
    outputs.dir(targetDir)
    destinationDirectory.set(targetDir)

    // Configure JVM compatibility
    sourceCompatibility = JavaVersion.VERSION_1_9.toString()
    targetCompatibility = JavaVersion.VERSION_1_9.toString()

    // Set the Java release version.
    options.release.set(9)

    // Ignore warnings about using 'requires transitive' on automatic modules.
    // not needed when compiling with recent JDKs, e.g. 17
    options.compilerArgs.add("-Xlint:-requires-transitive-automatic")

    // Patch the compileKotlinJvm output classes into the compilation so exporting packages works correctly.
    options.compilerArgs.addAll(
        listOf(
            "--patch-module",
            "$moduleName=${compileKotlinTask.destinationDirectory.get().asFile}"
        )
    )

    // Use the classpath of the compileKotlinJvm task.
    // Also, ensure that the module path is used instead of the classpath.
    classpath = compileKotlinTask.libraries
    modularity.inferModulePath.set(true)

    doFirst {
        logger.warn("Task destination directory: ${destinationDirectory.get().asFile}")
    }
}


// Configure the JAR task so that it will include the compiled module-info class file.
tasks.named("jvmJar", Jar::class) {
    manifest {
        attributes("Multi-Release" to true)
    }
    from(compileJavaModuleInfo.get().destinationDirectory) {
        into("META-INF/versions/9/")
    }
}

val jvmTest by tasks.getting(Test::class) {
    dependsOn(jvmTestAll)
    exclude(
        "**/AtomicfuBytecodeTest*",
        "**/AtomicfuReferenceJsTest*",
        "**/TopLevelGeneratedDeclarationsReflectionTest.*",
        "**/SyntheticFUFieldsTest.*"
    )
    // run them only for transformed code
}
