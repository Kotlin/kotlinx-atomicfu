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
    override fun apply(project: Project) = project.run {
        val pluginVersion = rootProject.buildscript.configurations.findByName("classpath")
            ?.allDependencies?.find { it.name == "atomicfu-gradle-plugin" }?.version
        extensions.add(EXTENSION_NAME, AtomicFUPluginExtension(pluginVersion))
        configureDependencies()
        configureTasks()
    }
}

private fun Project.configureDependencies() {
    withPluginWhenEvaluatedDependencies("kotlin") { version ->
        dependencies.add(
            if (config.transformJvm) COMPILE_ONLY_CONFIGURATION else IMPLEMENTATION_CONFIGURATION,
            getAtomicfuDependencyNotation(Platform.JVM, version)
        )
        dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.JVM, version))
    }
    withPluginWhenEvaluatedDependencies("kotlin2js") { version ->
        dependencies.add(
            if (config.transformJs) COMPILE_ONLY_CONFIGURATION else IMPLEMENTATION_CONFIGURATION,
            getAtomicfuDependencyNotation(Platform.JS, version)
        )
        dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.JS, version))
    }
    withPluginWhenEvaluatedDependencies("kotlin-multiplatform") { version ->
        configureMultiplatformPluginDependencies(version)
    }
}

private fun Project.configureTasks() {
    val config = config
    withPluginWhenEvaluated("kotlin") {
        if (config.transformJvm) {
            configureTransformTasks("compileTestKotlin") { sourceSet, transformedDir, originalDir ->
                createJvmTransformTask(sourceSet).configureJvmTask(
                    sourceSet.compileClasspath,
                    sourceSet.classesTaskName,
                    transformedDir,
                    originalDir,
                    config
                )
            }
        }
    }
    withPluginWhenEvaluated("kotlin2js") {
        if (config.transformJs) {
            configureTransformTasks("compileTestKotlin2Js") { sourceSet, transformedDir, originalDir ->
                createJsTransformTask(sourceSet).configureJsTask(
                    sourceSet.classesTaskName,
                    transformedDir,
                    originalDir,
                    config
                )
            }
        }
    }
    withPluginWhenEvaluated("kotlin-multiplatform") {
        configureMultiplatformPluginTasks()
    }
}

private enum class Platform(val suffix: String) {
    JVM("-jvm"),
    JS("-js"),
    NATIVE(""),
    MULTIPLATFORM("")
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

private val Project.config: AtomicFUPluginExtension
    get() = extensions.findByName(EXTENSION_NAME) as? AtomicFUPluginExtension ?: AtomicFUPluginExtension(null)

private fun getAtomicfuDependencyNotation(platform: Platform, version: String): String =
    "org.jetbrains.kotlinx:atomicfu${platform.suffix}:$version"

// Note "afterEvaluate" does nothing when the project is already in executed state, so we need
// a special check for this case
fun <T> Project.whenEvaluated(fn: Project.() -> T) {
    if (state.executed) {
        fn()
    } else {
        afterEvaluate { fn() }
    }
}

fun Project.withPluginWhenEvaluated(plugin: String, fn: Project.() -> Unit) {
    pluginManager.withPlugin(plugin) { whenEvaluated(fn) }
}

fun Project.withPluginWhenEvaluatedDependencies(plugin: String, fn: Project.(version: String) -> Unit) {
    withPluginWhenEvaluated(plugin) {
        config.dependenciesVersion?.let { fn(it) }
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

private fun KotlinCommonOptions.addFriendPaths(friendPathsFileCollection: FileCollection) {
    val argName = when (this) {
        is KotlinJvmOptions -> "-Xfriend-paths"
        is KotlinJsOptions -> "-Xfriend-modules"
        else -> return
    }
    freeCompilerArgs = freeCompilerArgs + "$argName=${friendPathsFileCollection.joinToString(",")}"
}

fun Project.configureMultiplatformPluginTasks() {
    val originalDirsByCompilation = hashMapOf<KotlinCompilation<*>, FileCollection>()
    val config = config
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
            val transformTask = when (target.platformType) {
                KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> {
                    if (!config.transformJvm) return@compilations // skip when transformation is turned off
                    project.createJvmTransformTask(compilation).configureJvmTask(
                        compilation.compileDependencyFiles,
                        compilation.compileAllTaskName,
                        transformedClassesDir,
                        originalClassesDirs,
                        config
                    )
                }
                KotlinPlatformType.js -> {
                    if (!config.transformJs) return@compilations // skip when transformation is turned off
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
            classesDirs.setFrom(transformedClassesDir)
            classesDirs.builtBy(transformTask)
            (tasks.findByName(target.artifactsTaskName) as? Jar)?.apply {
                setupJarManifest(multiRelease = config.variant.toVariant() == Variant.BOTH)
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

                compilation.compileKotlinTask.doFirst {
                    compilation.kotlinOptions.addFriendPaths(originalMainClassesDirs)
                }
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
    if (rootProject.findProperty("kotlin.mpp.enableGranularSourceSetsMetadata").toString().toBoolean()) {
        val mainConfigurationName = project.extensions.getByType(KotlinMultiplatformExtension::class.java).sourceSets
                .getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
                .compileOnlyConfigurationName
        dependencies.add(mainConfigurationName, getAtomicfuDependencyNotation(Platform.MULTIPLATFORM, version))

        val testConfigurationName = project.extensions.getByType(KotlinMultiplatformExtension::class.java).sourceSets
                .getByName(KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME)
                .implementationConfigurationName
        dependencies.add(testConfigurationName, getAtomicfuDependencyNotation(Platform.MULTIPLATFORM, version))

        // For each source set that is only used in Native compilations, add an implementation dependency so that it
        // gets published and is properly consumed as a transitive dependency:
        sourceSetsByCompilation().forEach { (sourceSet, compilations) ->
            val isSharedNativeSourceSet = compilations.all {
                it.platformType == KotlinPlatformType.common || it.platformType == KotlinPlatformType.native
            }
            if (isSharedNativeSourceSet) {
                val configuration = sourceSet.implementationConfigurationName
                dependencies.add(configuration, getAtomicfuDependencyNotation(Platform.MULTIPLATFORM, version))
            }
        }
    } else {
        sourceSetsByCompilation().forEach { (sourceSet, compilations) ->
            val platformTypes = compilations.map { it.platformType }.toSet()
            val compilationNames = compilations.map { it.compilationName }.toSet()
            if (compilationNames.size != 1)
                error("Source set '${sourceSet.name}' of project '$name' is part of several compilations $compilationNames")
            val compilationType = compilationNames.single().compilationNameToType()
                    ?: return@forEach // skip unknown compilations
            val platform =
                    if (platformTypes.size > 1) Platform.MULTIPLATFORM else // mix of platform types -> "common"
                        when (platformTypes.single()) {
                            KotlinPlatformType.common -> Platform.MULTIPLATFORM
                            KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> Platform.JVM
                            KotlinPlatformType.js -> Platform.JS
                            KotlinPlatformType.native -> Platform.NATIVE
                            else -> TODO()
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
}

fun Project.configureTransformTasks(
    testTaskName: String,
    createTransformTask: (sourceSet: SourceSet, transformedDir: File, originalDir: FileCollection) -> Task
) {
    val config = config
    sourceSets.all { sourceSet ->
        val compilationType = sourceSet.name.sourceSetNameToType()
            ?: return@all // skip unknown types
        val classesDirs = (sourceSet.output.classesDirs as ConfigurableFileCollection).from as Collection<Any>
        // make copy of original classes directory
        val originalClassesDirs: FileCollection = project.files(classesDirs.toTypedArray()).filter { it.exists() }
        (sourceSet as ExtensionAware).extensions.add(ORIGINAL_DIR_NAME, originalClassesDirs)
        val transformedClassesDir =
            project.buildDir.resolve("classes/atomicfu/${sourceSet.name}")
        // make transformedClassesDir the source path for output.classesDirs
        (sourceSet.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)
        val transformTask = createTransformTask(sourceSet, transformedClassesDir, originalClassesDirs)
        //now transformTask is responsible for compiling this source set into the classes directory
        sourceSet.compiledBy(transformTask)
        (tasks.findByName(sourceSet.jarTaskName) as? Jar)?.apply {
            setupJarManifest(multiRelease = config.variant.toVariant() == Variant.BOTH)
        }
        // test should compile and run against original production binaries
        if (compilationType == CompilationType.TEST) {
            val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            val originalMainClassesDirs = project.files(
                // use Callable because there is no guarantee that main is configured before test
                Callable { (mainSourceSet as ExtensionAware).extensions.getByName(ORIGINAL_DIR_NAME) as FileCollection }
            )

            (tasks.findByName(testTaskName) as? AbstractCompile)?.run {
                classpath =
                    originalMainClassesDirs + sourceSet.compileClasspath - mainSourceSet.output.classesDirs

                (this as? KotlinCompile<*>)?.doFirst {
                    kotlinOptions.addFriendPaths(originalMainClassesDirs)
                }
            }

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
    config: AtomicFUPluginExtension
): ConventionTask =
    apply {
        dependsOn(classesTaskName)
        classPath = classpath
        inputFiles = originalClassesDir
        outputDir = transformedClassesDir
        variant = config.variant
        verbose = config.verbose
    }

fun AtomicFUTransformJsTask.configureJsTask(
    classesTaskName: String,
    transformedClassesDir: File,
    originalClassesDir: FileCollection,
    config: AtomicFUPluginExtension
): ConventionTask =
    apply {
        dependsOn(classesTaskName)
        inputFiles = originalClassesDir
        outputDir = transformedClassesDir
        verbose = config.verbose
    }

fun Jar.setupJarManifest(multiRelease: Boolean) {
    if (multiRelease) {
        manifest.attributes.apply {
            put("Multi-Release", "true")
        }
    }
}

val Project.sourceSets: SourceSetContainer
    get() = convention.getPlugin(JavaPluginConvention::class.java).sourceSets

class AtomicFUPluginExtension(pluginVersion: String?) {
    var dependenciesVersion = pluginVersion
    var transformJvm = true
    var transformJs = true
    var variant: String = "FU"
    var verbose: Boolean = false
}

@CacheableTask
open class AtomicFUTransformTask : ConventionTask() {
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    lateinit var inputFiles: FileCollection

    @OutputDirectory
    lateinit var outputDir: File

    @Classpath
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
    @PathSensitive(PathSensitivity.RELATIVE)
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
