/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.*
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.internal.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.*
import org.gradle.api.tasks.testing.*
import org.gradle.jvm.tasks.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import java.io.*
import java.util.*
import java.util.concurrent.*

private const val EXTENSION_NAME = "atomicfu"
private const val ORIGINAL_DIR_NAME = "originalClassesDir"
private const val COMPILE_ONLY_CONFIGURATION = "compileOnly"
private const val IMPLEMENTATION_CONFIGURATION = "implementation"
private const val TEST_IMPLEMENTATION_CONFIGURATION = "testImplementation"

open class AtomicFUGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add(EXTENSION_NAME, AtomicFUPluginExtension())
        project.configureDependencies()
        project.configureTasks()
    }
}

private fun Project.configureDependencies() {
    val version = project.rootProject.buildscript.configurations.findByName("classpath")
        ?.allDependencies?.find { it.name == "atomicfu-gradle-plugin" }?.version ?: return
    withPlugin("kotlin") {
        dependencies.add(COMPILE_ONLY_CONFIGURATION, getAtomicfuDependencyNotation(Platform.JVM, version))
        dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.JVM, version))
    }
    withPlugin("kotlin2js") {
        dependencies.add(COMPILE_ONLY_CONFIGURATION, getAtomicfuDependencyNotation(Platform.JS, version))
        dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.JS, version))
    }
    withPlugin("kotlin-native") {
        dependencies.add(IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.NATIVE, version))
        dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.NATIVE, version))
    }
    withPlugin("kotlin-platform-common") {
        dependencies.add(COMPILE_ONLY_CONFIGURATION, getAtomicfuDependencyNotation(Platform.COMMON, version))
        dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.COMMON, version))
    }
    withPlugin("kotlin-multiplatform") {
        configureMultiplatformPluginDependencies(version)
    }
}

private fun Project.configureTasks() {
    withPluginAfterEvaluate("kotlin") {
        configureTransformTasks("compileTestKotlin") { sourceSet, transformedDir, originalDir, config ->
            createJvmTransformTask(sourceSet).configureJvmTask(
                sourceSet.compileClasspath,
                sourceSet.classesTaskName,
                transformedDir,
                originalDir,
                config
            )
        }
    }
    withPluginAfterEvaluate("kotlin2js") {
        configureTransformTasks("compileTestKotlin2Js") { sourceSet, transformedDir, originalDir, config ->
            createJsTransformTask(sourceSet).configureJsTask(
                sourceSet.classesTaskName,
                transformedDir,
                originalDir,
                config
            )
        }
    }
    withPluginAfterEvaluate("kotlin-multiplatform") {
        configureMultiplatformPluginTasks()
    }
}

private enum class Platform(val suffix: String) {
    JVM(""),
    JS("-js"),
    NATIVE("-native"),
    COMMON("-common")
}

private enum class CompilationType { MAIN, TEST }

private fun String.compilationNameToType(): CompilationType? = when (this) {
    KotlinCompilation.MAIN_COMPILATION_NAME -> CompilationType.MAIN
    KotlinCompilation.TEST_COMPILATION_NAME -> CompilationType.TEST
    else -> null
}

private fun String.sourceSetNameToType(): CompilationType? = when (this) {
    SourceSet.MAIN_SOURCE_SET_NAME -> CompilationType.MAIN
    SourceSet.TEST_SOURCE_SET_NAME -> CompilationType.TEST
    else -> null
}

private fun Project.config(): AtomicFUPluginExtension? =
    extensions.findByName(EXTENSION_NAME) as? AtomicFUPluginExtension

private fun getAtomicfuDependencyNotation(platform: Platform, version: String): String =
    "org.jetbrains.kotlinx:atomicfu${platform.suffix}:$version"

fun Project.withPlugin(plugin: String, fn: Project.() -> Unit) {
    pluginManager.withPlugin(plugin) { fn() }
}

fun Project.withPluginAfterEvaluate(plugin: String, fn: Project.() -> Unit) {
    withPlugin(plugin) {
        // Note "afterEvaluate" does nothing when the project is already in executed state, so we need
        // a special check for this case
        if (state.executed) {
            fn()
        } else {
            afterEvaluate(fn)
        }
    }
}

fun Project.withKotlinTargets(fn: (KotlinTarget) -> Unit) {
    extensions.findByType(KotlinProjectExtension::class.java)?.let { kotlinExtension ->
        val targetsExtension = (kotlinExtension as? ExtensionAware)?.extensions?.findByName("targets")
        @Suppress("UNCHECKED_CAST")
        val targets = targetsExtension as NamedDomainObjectContainer<KotlinTarget>
        // find all compilations given sourceSet belongs to
        targets.all { target -> fn(target) }
    }
}

fun Project.configureMultiplatformPluginTasks() {
    val originalDirsByCompilation = hashMapOf<KotlinCompilation<*>, FileCollection>()
    val config = config()
    withKotlinTargets { target ->
        if (target.platformType == KotlinPlatformType.common || target.platformType == KotlinPlatformType.native) {
            return@withKotlinTargets // skip the common & native targets -- no transformation for them
        }
        target.compilations.all compilations@{ compilation ->
            val compilationType = compilation.name.compilationNameToType()
                ?: return@compilations // skip unknown compilations
            val classesDirs = compilation.output.classesDirs
            // make copy of original classes directory
            val originalClassesDirs: FileCollection =
                project.files(classesDirs.from.toTypedArray()).filter { it.exists() }
            originalDirsByCompilation[compilation] = originalClassesDirs
            val transformedClassesDir =
                project.buildDir.resolve("classes/atomicfu/${target.name}/${compilation.name}")
            classesDirs.setFrom(transformedClassesDir)
            val transformTask = when (target.platformType) {
                KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> {
                    project.createJvmTransformTask(compilation).configureJvmTask(
                        compilation.compileDependencyFiles,
                        compilation.compileAllTaskName,
                        transformedClassesDir,
                        originalClassesDirs,
                        config
                    )
                }
                KotlinPlatformType.js -> {
                    project.createJsTransformTask(compilation).configureJsTask(
                        compilation.compileAllTaskName,
                        transformedClassesDir,
                        originalClassesDirs,
                        config
                    )
                }
                else -> error("Unsupported transformation platform '${target.platformType}'")
            }
            //now transformTask is responsible for compiling this source set into the classes directory
            classesDirs.builtBy(transformTask)
            (tasks.findByName(target.artifactsTaskName) as? Jar)?.apply {
                setupJarManifest(multiRelease = config?.variant?.toVariant() == Variant.BOTH)
            }
            // test should compile and run against original production binaries
            if (compilationType == CompilationType.TEST) {
                val mainCompilation =
                    compilation.target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                val originalMainClassesDirs = project.files(
                    // use Callable because there is no guarantee that main is configured before test
                    Callable { originalDirsByCompilation[mainCompilation]!! }
                )

                (tasks.findByName(compilation.compileKotlinTaskName) as? AbstractCompile)?.classpath =
                    originalMainClassesDirs + compilation.compileDependencyFiles - mainCompilation.output.classesDirs

                (tasks.findByName("${target.name}${compilation.name.capitalize()}") as? Test)?.classpath =
                    originalMainClassesDirs + (compilation as KotlinCompilationToRunnableFiles).runtimeDependencyFiles - mainCompilation.output.classesDirs
            }
        }
    }
}

fun Project.sourceSetsByCompilation(): Map<KotlinSourceSet, List<KotlinCompilation<*>>> {
    val sourceSetsByCompilation = hashMapOf<KotlinSourceSet, MutableList<KotlinCompilation<*>>>()
    withKotlinTargets { target ->
        target.compilations.forEach { compilation ->
            compilation.allKotlinSourceSets.forEach { sourceSet ->
                sourceSetsByCompilation.getOrPut(sourceSet) { mutableListOf() }.add(compilation)
            }
        }
    }
    return sourceSetsByCompilation
}

fun Project.configureMultiplatformPluginDependencies(version: String) {
    sourceSetsByCompilation().forEach { (sourceSet, compilations) ->
        val platformTypes = compilations.map { it.platformType }.toSet()
        val compilationNames = compilations.map { it.compilationName }.toSet()
        if (compilationNames.size != 1)
            error("Source set '${sourceSet.name}' of project '$name' is part of several compilations $compilationNames")
        val compilationType = compilationNames.single().compilationNameToType()
            ?: return@forEach // skip unknown compilations
        val platform =
            if (platformTypes.size > 1) Platform.COMMON else // mix of platform types -> "common"
                when (platformTypes.single()) {
                    KotlinPlatformType.common -> Platform.COMMON
                    KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> Platform.JVM
                    KotlinPlatformType.js -> Platform.JS
                    KotlinPlatformType.native -> Platform.NATIVE
                }
        val configurationName = when {
            // impl dependency for native (there is no transformation)
            platform == Platform.NATIVE -> sourceSet.implementationConfigurationName
            // compileOnly dependency for main compilation (commonMain, jvmMain, jsMain)
            compilationType == CompilationType.MAIN -> sourceSet.compileOnlyConfigurationName
            // impl dependency for tests
            else -> sourceSet.implementationConfigurationName
        }
        dependencies.add(configurationName, getAtomicfuDependencyNotation(platform, version))
    }
}

fun Project.configureTransformTasks(
    testTaskName: String,
    createTransformTask: (sourceSet: SourceSet, transformedDir: File, originalDir: FileCollection, config: AtomicFUPluginExtension?) -> Task
) {
    sourceSets.all { sourceSet ->
        val compilationType = sourceSet.name.sourceSetNameToType()
            ?: return@all // skip unknown types
        val config = extensions.findByName(EXTENSION_NAME) as? AtomicFUPluginExtension
        val classesDirs = (sourceSet.output.classesDirs as ConfigurableFileCollection).from as Collection<Any>
        // make copy of original classes directory
        val originalClassesDirs: FileCollection = project.files(classesDirs.toTypedArray()).filter { it.exists() }
        (sourceSet as ExtensionAware).extensions.add(ORIGINAL_DIR_NAME, originalClassesDirs)
        val transformedClassesDir =
            project.buildDir.resolve("classes/atomicfu/${sourceSet.name}")
        // make transformedClassesDir the source path for output.classesDirs
        (sourceSet.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)
        val transformTask = createTransformTask(sourceSet, transformedClassesDir, originalClassesDirs, config)
        //now transformTask is responsible for compiling this source set into the classes directory
        sourceSet.compiledBy(transformTask)
        (tasks.findByName(sourceSet.jarTaskName) as? Jar)?.apply {
            setupJarManifest(multiRelease = config?.variant?.toVariant() == Variant.BOTH)
        }
        // test should compile and run against original production binaries
        if (compilationType == CompilationType.TEST) {
            val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            val originalMainClassesDirs = project.files(
                // use Callable because there is no guarantee that main is configured before test
                Callable { (mainSourceSet as ExtensionAware).extensions.getByName(ORIGINAL_DIR_NAME) as FileCollection }
            )

            (tasks.findByName(testTaskName) as? AbstractCompile)?.classpath =
                originalMainClassesDirs + sourceSet.compileClasspath - mainSourceSet.output.classesDirs

            // todo: fix test runtime classpath for JS?
            (tasks.findByName(JavaPlugin.TEST_TASK_NAME) as? Test)?.classpath =
                originalMainClassesDirs + sourceSet.runtimeClasspath - mainSourceSet.output.classesDirs
        }
    }
}

fun String.toVariant(): Variant = enumValueOf(toUpperCase(Locale.US))

fun Project.createJvmTransformTask(compilation: KotlinCompilation<*>): AtomicFUTransformTask =
    tasks.create(
        "transform${compilation.target.name.capitalize()}${compilation.name.capitalize()}Atomicfu",
        AtomicFUTransformTask::class.java
    )

fun Project.createJsTransformTask(compilation: KotlinCompilation<*>): AtomicFUTransformJsTask =
    tasks.create(
        "transform${compilation.target.name.capitalize()}${compilation.name.capitalize()}Atomicfu",
        AtomicFUTransformJsTask::class.java
    )

fun Project.createJvmTransformTask(sourceSet: SourceSet): AtomicFUTransformTask =
    tasks.create(sourceSet.getTaskName("transform", "atomicfuClasses"), AtomicFUTransformTask::class.java)

fun Project.createJsTransformTask(sourceSet: SourceSet): AtomicFUTransformJsTask =
    tasks.create(sourceSet.getTaskName("transform", "atomicfuJsFiles"), AtomicFUTransformJsTask::class.java)

fun AtomicFUTransformTask.configureJvmTask(
    classpath: FileCollection,
    classesTaskName: String,
    transformedClassesDir: File,
    originalClassesDir: FileCollection,
    config: AtomicFUPluginExtension?
): ConventionTask =
    apply {
        dependsOn(classesTaskName)
        classPath = classpath
        inputFiles = originalClassesDir
        outputDir = transformedClassesDir
        config?.let {
            variant = it.variant
            verbose = it.verbose
        }
    }

fun AtomicFUTransformJsTask.configureJsTask(
    classesTaskName: String,
    transformedClassesDir: File,
    originalClassesDir: FileCollection,
    config: AtomicFUPluginExtension?
): ConventionTask =
    apply {
        dependsOn(classesTaskName)
        inputFiles = originalClassesDir
        outputDir = transformedClassesDir
        config?.let {
            verbose = it.verbose
        }
    }

fun Jar.setupJarManifest(multiRelease: Boolean, classifier: String = "") {
    this.classifier = classifier // todo: why we overwrite jar's classifier?
    if (multiRelease) {
        manifest.attributes.apply {
            put("Multi-Release", "true")
        }
    }
}

val Project.sourceSets: SourceSetContainer
    get() = convention.getPlugin(JavaPluginConvention::class.java).sourceSets

class AtomicFUPluginExtension {
    var variant: String = "FU"
    var verbose: Boolean = false
}

@CacheableTask
open class AtomicFUTransformTask : ConventionTask() {
    @InputFiles
    lateinit var inputFiles: FileCollection
    @OutputDirectory
    lateinit var outputDir: File
    @InputFiles
    lateinit var classPath: FileCollection
    @Input
    var variant = "FU"
    @Input
    var verbose = false

    @TaskAction
    fun transform() {
        val cp = classPath.files.map { it.absolutePath }
        inputFiles.files.forEach { inputDir ->
            AtomicFUTransformer(cp, inputDir, outputDir).let { t ->
                t.variant = variant.toVariant()
                t.verbose = verbose
                t.transform()
            }
        }
    }
}

@CacheableTask
open class AtomicFUTransformJsTask : ConventionTask() {
    @InputFiles
    lateinit var inputFiles: FileCollection
    @OutputDirectory
    lateinit var outputDir: File
    @Input
    var verbose = false

    @TaskAction
    fun transform() {
        inputFiles.files.forEach { inputDir ->
            AtomicFUTransformerJS(inputDir, outputDir).let { t ->
                t.verbose = verbose
                t.transform()
            }
        }
    }
}