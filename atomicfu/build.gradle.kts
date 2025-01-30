import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("kotlin-multiplatform-conventions")
    id("kotlin-multiplatform-publish-conventions")
}

kotlin {

    // JS -- always
    js(IR) {
        moduleName = "kotlinx-atomicfu"
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

        val jsAndWasmSharedMain by creating {
            dependsOn(commonMain.get())
        }

        jsMain {
            dependsOn(jsAndWasmSharedMain)
            dependencies {
                compileOnly("org.jetbrains.kotlin:kotlin-dom-api-compat")
            }
        }

        jsTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-js")
            }
        }

        wasmJsMain {
            dependsOn(jsAndWasmSharedMain)
        }

        wasmJsTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-wasm-js")
            }
        }


        wasmWasiMain {
            dependsOn(jsAndWasmSharedMain)
        }

        wasmWasiTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-wasm-wasi")
            }
        }

        jvmTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("org.jetbrains.kotlin:kotlin-test-junit")
                implementation("org.jetbrains.kotlinx:lincheck:2.34")
                implementation(libs.junit.junit)
            }
        }
    }
}

// Support of all non-deprecated targets from the official tier list: https://kotlinlang.org/docs/native-target-support.html
kotlin {
    // Tier 1
    macosX64()
    macosArm64()
    iosSimulatorArm64()
    iosX64()

    // Tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()
    iosArm64()

    // Tier 3
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    mingwX64()
    watchosDeviceArm64()

    @Suppress("DEPRECATION") //https://github.com/Kotlin/kotlinx-atomicfu/issues/207
    linuxArm32Hfp()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate() 

    sourceSets {
        val nativeUnixLikeMain by creating { dependsOn(nativeMain.get()) }
        
        
        val androidNative64BitMain by creating { dependsOn(nativeMain.get()) }
        androidNative64BitMain.also {
            androidNativeArm64Main.get().dependsOn(it)
            androidNativeX64Main.get().dependsOn(it)
        }

        val androidNative32BitMain by creating { dependsOn(nativeMain.get()) }
        androidNative32BitMain.let {
            androidNativeArm32Main.get().dependsOn(it)
            androidNativeX86Main.get().dependsOn(it)
        }

        val linux64Main by creating { dependsOn(nativeUnixLikeMain) }
        linux64Main.let {
            linuxX64Main.get().dependsOn(it)
            linuxArm64Main.get().dependsOn(it)
        }

        val linux32Main by creating { dependsOn(nativeUnixLikeMain) }
        linux32Main.let {
            linuxArm32HfpMain.get().dependsOn(it)
        }

        val apple64Main by creating { dependsOn(nativeUnixLikeMain) }
        apple64Main.let {
            watchosDeviceArm64Main.get().dependsOn(it)
            iosArm64Main.get().dependsOn(it)
            tvosArm64Main.get().dependsOn(it)
            tvosX64Main.get().dependsOn(it)
            tvosSimulatorArm64Main.get().dependsOn(it)
            watchosX64Main.get().dependsOn(it)
            watchosSimulatorArm64Main.get().dependsOn(it)
            iosX64Main.get().dependsOn(it)
            iosSimulatorArm64Main.get().dependsOn(it)
            macosX64Main.get().dependsOn(it)
            macosArm64Main.get().dependsOn(it)
        }

        val apple32Main by creating { dependsOn(nativeUnixLikeMain) }
        apple32Main.let {
            watchosArm32Main.get().dependsOn(it)
            watchosArm64Main.get().dependsOn(it) // Uses Int for timespec
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
}

val transformer: Configuration by configurations.creating

dependencies {
    transformer(project(":atomicfu-transformer"))
}

// ==== CONFIGURE JVM =====

val classesPreAtomicFuDir = file("${layout.buildDirectory.get()}/classes/kotlin/jvm/test")
val classesPostTransformFU = file("${layout.buildDirectory.get()}/classes/kotlin/jvm/postTransformedFU")
val classesPostTransformVH = file("${layout.buildDirectory.get()}/classes/kotlin/jvm/postTransformedVH")
val classesPostTransformBOTH = file("${layout.buildDirectory.get()}/classes/kotlin/jvm/postTransformedBOTH")

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

val transformFU by tasks.registering(JavaExec::class) {
    dependsOn(tasks.get("compileTestKotlinJvm"))
    mainClass = "kotlinx.atomicfu.transformer.AtomicFUTransformerKt"
    args(classesPreAtomicFuDir, classesPostTransformFU, "FU")
    classpath(transformer)
    inputs.dir(classesPreAtomicFuDir)
    outputs.dir(classesPostTransformFU)
}

val transformBOTH by tasks.registering(JavaExec::class) {
    dependsOn("compileTestKotlinJvm")
    mainClass = "kotlinx.atomicfu.transformer.AtomicFUTransformerKt"
    args(classesPreAtomicFuDir, classesPostTransformBOTH, "BOTH")
    classpath = transformer
    inputs.dir(classesPreAtomicFuDir)
    outputs.dir(classesPostTransformBOTH)
}


val transformVH by tasks.registering(JavaExec::class) {
    dependsOn("compileTestKotlinJvm")
    mainClass = "kotlinx.atomicfu.transformer.AtomicFUTransformerKt"
    args(classesPreAtomicFuDir, classesPostTransformVH, "VH")
    classpath(transformer)
    inputs.dir(classesPreAtomicFuDir)
    outputs.dir(classesPostTransformVH)
}


val transformedTestFU_current by tasks.registering(Test::class) {
    dependsOn(transformFU)
    classpath = files(configurations.getByName("jvmTestRuntimeClasspath"), classesPostTransformFU)
    testClassesDirs = project.files(classesPostTransformFU)
    exclude("**/*LFTest.*", "**/TraceToStringTest.*", "**/AtomicfuReferenceJsTest.*")
    filter { isFailOnNoMatchingTests = false }
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
        "**/AtomicfuReferenceJsTest.*"
    )
    filter { isFailOnNoMatchingTests = false }
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
        "**/AtomicfuReferenceJsTest.*"
    )
    filter { isFailOnNoMatchingTests = false }

    onlyIf { currentTask ->
        logger.info("Current java version for task ${currentTask.name} is : ${JavaVersion.current()}")
        JavaVersion.current().ordinal >= JavaVersion.VERSION_1_9.ordinal
    }
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

