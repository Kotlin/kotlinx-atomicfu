/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.*
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.internal.*
import org.gradle.api.plugins.*
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.*
import org.gradle.api.tasks.testing.*
import org.gradle.jvm.tasks.*
import org.gradle.util.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.*
import java.io.*
import java.util.*
import java.util.concurrent.*
import org.jetbrains.kotlin.gradle.targets.js.*
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.tasks.*
import org.jetbrains.kotlinx.atomicfu.gradle.*
import javax.inject.Inject

private const val EXTENSION_NAME = "atomicfu"
private const val ORIGINAL_DIR_NAME = "originalClassesDir"
private const val COMPILE_ONLY_CONFIGURATION = "compileOnly"
private const val IMPLEMENTATION_CONFIGURATION = "implementation"
private const val TEST_IMPLEMENTATION_CONFIGURATION = "testImplementation"
// If the project uses KGP <= 1.6.20, only JS IR compiler plugin is available, and it is turned on via setting this property.
// The property is supported for backwards compatibility.
private const val ENABLE_JS_IR_TRANSFORMATION_LEGACY = "kotlinx.atomicfu.enableIrTransformation"
private const val ENABLE_JS_IR_TRANSFORMATION = "kotlinx.atomicfu.enableJsIrTransformation"
private const val ENABLE_JVM_IR_TRANSFORMATION = "kotlinx.atomicfu.enableJvmIrTransformation"
private const val ENABLE_NATIVE_IR_TRANSFORMATION = "kotlinx.atomicfu.enableNativeIrTransformation"
private const val MIN_SUPPORTED_GRADLE_VERSION = "7.0"
private const val MIN_SUPPORTED_KGP_VERSION = "1.7.0"

open class AtomicFUGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) = project.run {
        checkCompatibility()
        val pluginVersion = rootProject.buildscript.configurations.findByName("classpath")
            ?.allDependencies?.find { it.name == "atomicfu-gradle-plugin" }?.version
        extensions.add(EXTENSION_NAME, AtomicFUPluginExtension(pluginVersion))
        applyAtomicfuCompilerPlugin()
        configureDependencies()
        configureTasks()
    }
}

private fun Project.checkCompatibility() {
    val currentGradleVersion = GradleVersion.current()
    val kotlinVersion = getKotlinVersion()
    val minSupportedVersion = GradleVersion.version(MIN_SUPPORTED_GRADLE_VERSION)
    if (currentGradleVersion < minSupportedVersion) {
        throw GradleException(
            "The current Gradle version is not compatible with Atomicfu gradle plugin. " +
                    "Please use Gradle $MIN_SUPPORTED_GRADLE_VERSION or newer, or the previous version of Atomicfu gradle plugin."
        )
    }
    if (!kotlinVersion.atLeast(1, 7, 0)) {
        throw GradleException(
            "The current Kotlin gradle plugin version is not compatible with Atomicfu gradle plugin. " +
                    "Please use Kotlin $MIN_SUPPORTED_KGP_VERSION or newer, or the previous version of Atomicfu gradle plugin."
        )
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
    withPluginWhenEvaluatedDependencies("org.jetbrains.kotlin.js") { version ->
        dependencies.add(
            if (config.transformJs) COMPILE_ONLY_CONFIGURATION else IMPLEMENTATION_CONFIGURATION,
            getAtomicfuDependencyNotation(Platform.JS, version)
        )
        dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.JS, version))
        addCompilerPluginDependency()
    }
    withPluginWhenEvaluatedDependencies("kotlin-multiplatform") { version ->
        configureMultiplatformPluginDependencies(version)
    }
}

private fun Project.configureTasks() {
    val config = config
    withPluginWhenEvaluated("kotlin") {
        if (config.transformJvm) configureJvmTransformation()
    }
    withPluginWhenEvaluated("org.jetbrains.kotlin.js") {
        if (config.transformJs) configureJsTransformation()
    }
    withPluginWhenEvaluated("kotlin-multiplatform") {
        configureMultiplatformTransformation()
    }
}

private data class KotlinVersion(val major: Int, val minor: Int, val patch: Int)

private fun Project.getKotlinVersion(): KotlinVersion {
    val kotlinVersion = getKotlinPluginVersion()
    val (major, minor) = kotlinVersion
        .split('.')
        .take(2)
        .map { it.toInt() }
    val patch = kotlinVersion.substringAfterLast('.').substringBefore('-').toInt()
    return KotlinVersion(major, minor, patch)
}

private fun KotlinVersion.atLeast(major: Int, minor: Int, patch: Int) =
    this.major == major && (this.minor == minor && this.patch >= patch || this.minor > minor) || this.major > major

// kotlinx-atomicfu compiler plugin is available for KGP >= 1.6.20
private fun Project.isCompilerPluginAvailable() = getKotlinVersion().atLeast(1, 6, 20)

private fun Project.applyAtomicfuCompilerPlugin() {
    val kotlinVersion = getKotlinVersion()
    // for KGP >= 1.7.20:
    // compiler plugin for JS IR is applied via the property `kotlinx.atomicfu.enableJsIrTransformation`
    // compiler plugin for JVM IR is applied via the property `kotlinx.atomicfu.enableJvmIrTransformation`
    if (kotlinVersion.atLeast(1, 7, 20)) {
        plugins.apply(AtomicfuKotlinGradleSubplugin::class.java)
        extensions.getByType(AtomicfuKotlinGradleSubplugin.AtomicfuKotlinGradleExtension::class.java).apply {
            isJsIrTransformationEnabled = rootProject.getBooleanProperty(ENABLE_JS_IR_TRANSFORMATION)
            isJvmIrTransformationEnabled = rootProject.getBooleanProperty(ENABLE_JVM_IR_TRANSFORMATION)
            isNativeIrTransformationEnabled = rootProject.getBooleanProperty(ENABLE_NATIVE_IR_TRANSFORMATION)
        }
    } else {
        // for KGP >= 1.6.20 && KGP <= 1.7.20:
        // compiler plugin for JS IR is applied via the property `kotlinx.atomicfu.enableIrTransformation`
        // compiler plugin for JVM IR is not supported yet
        if (kotlinVersion.atLeast(1, 6, 20)) {
            if (rootProject.getBooleanProperty(ENABLE_JS_IR_TRANSFORMATION_LEGACY)) {
                plugins.apply(AtomicfuKotlinGradleSubplugin::class.java)
            }
        }
    }
}

private fun Project.getBooleanProperty(name: String) =
    rootProject.findProperty(name)?.toString()?.toBooleanStrict() ?: false

private fun String.toBooleanStrict(): Boolean = when (this) {
    "true" -> true
    "false" -> false
    else -> throw IllegalArgumentException("The string doesn't represent a boolean value: $this")
}

private fun Project.needsJsIrTransformation(target: KotlinTarget): Boolean =
    (rootProject.getBooleanProperty(ENABLE_JS_IR_TRANSFORMATION) || rootProject.getBooleanProperty(ENABLE_JS_IR_TRANSFORMATION_LEGACY))
            && target.isJsIrTarget()

private fun Project.needsJvmIrTransformation(target: KotlinTarget): Boolean =
    rootProject.getBooleanProperty(ENABLE_JVM_IR_TRANSFORMATION) &&
            (target.platformType == KotlinPlatformType.jvm || target.platformType == KotlinPlatformType.androidJvm)

private fun Project.needsNativeIrTransformation(target: KotlinTarget): Boolean =
        rootProject.getBooleanProperty(ENABLE_NATIVE_IR_TRANSFORMATION) &&
                (target.platformType == KotlinPlatformType.native)

private fun KotlinTarget.isJsIrTarget() = (this is KotlinJsTarget && this.irTarget != null) || this is KotlinJsIrTarget

private fun Project.addCompilerPluginDependency() {
    if (isCompilerPluginAvailable()) {
        withKotlinTargets { target ->
            if (target.isJsIrTarget()) {
                target.compilations.forEach { kotlinCompilation ->
                    kotlinCompilation.dependencies {
                        if (getKotlinVersion().atLeast(1, 7, 10)) {
                            // since Kotlin 1.7.10 `kotlinx-atomicfu-runtime` is published and should be added directly
                            implementation("org.jetbrains.kotlin:kotlinx-atomicfu-runtime:${getKotlinPluginVersion()}")
                        } else {
                            implementation("org.jetbrains.kotlin:atomicfu:${getKotlinPluginVersion()}")
                        }
                    }
                }
            }
        }
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
    extensions.findByType(KotlinTargetsContainer::class.java)?.let { kotlinExtension ->
        // find all compilations given sourceSet belongs to
        kotlinExtension.targets
            .all { target -> fn(target) }
    }
}

private fun KotlinCompile<*>.setFriendPaths(friendPathsFileCollection: FileCollection) {
    val (majorVersion, minorVersion) = project.getKotlinPluginVersion()
        .split('.')
        .take(2)
        .map { it.toInt() }
    if (majorVersion == 1 && minorVersion < 7) {
        (this as? AbstractKotlinCompile<*>)?.friendPaths?.from(friendPathsFileCollection)
    } else {
        // See KT-KT-54167 (works only for KGP 1.7.0+)
        (this as BaseKotlinCompile).friendPaths.from(friendPathsFileCollection)
    }
}

fun Project.configureJvmTransformation() {
    if (kotlinExtension is KotlinJvmProjectExtension || kotlinExtension is KotlinAndroidProjectExtension) {
        val target = (kotlinExtension as KotlinSingleTargetExtension<*>).target
        if (!needsJvmIrTransformation(target)) {
            configureTransformationForTarget((kotlinExtension as KotlinSingleTargetExtension<*>).target)
        }
    }
}

fun Project.configureJsTransformation() {
    val target = (kotlinExtension as KotlinJsProjectExtension).js()
    if (!needsJsIrTransformation(target)) {
        configureTransformationForTarget((kotlinExtension as KotlinJsProjectExtension).js())
    }
}

fun Project.configureMultiplatformTransformation() =
    withKotlinTargets { target ->
        // Skip transformation for common and native targets and in case IR transformation by the compiler plugin is enabled (for JVM or JS targets)
        if (target.platformType == KotlinPlatformType.common  || target.platformType == KotlinPlatformType.native ||
                needsJvmIrTransformation(target) || needsJsIrTransformation(target))
            return@withKotlinTargets
        configureTransformationForTarget(target)
    }

private fun Project.configureTransformationForTarget(target: KotlinTarget) {
    val originalDirsByCompilation = hashMapOf<KotlinCompilation<*>, FileCollection>()
    val config = config
    target.compilations.all compilations@{ compilation ->
        val compilationType = compilation.name.compilationNameToType()
            ?: return@compilations // skip unknown compilations
        val classesDirs = compilation.output.classesDirs
        // make copy of original classes directory
        @Suppress("UNCHECKED_CAST")
        val compilationTask = compilation.compileTaskProvider as TaskProvider<KotlinCompileTool>
        val originalDestinationDirectory = project.layout.buildDirectory
            .dir("classes/atomicfu-orig/${target.name}/${compilation.name}")
        compilationTask.configure {
            if (it is Kotlin2JsCompile) {
                @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "EXPOSED_PARAMETER_TYPE")
                it.defaultDestinationDirectory.value(originalDestinationDirectory)
            } else {
                it.destinationDirectory.value(originalDestinationDirectory)
            }
        }
        val originalClassesDirs: FileCollection = project.objects.fileCollection().from(
            compilationTask.flatMap { it.destinationDirectory }
        )
        originalDirsByCompilation[compilation] = originalClassesDirs
        val transformedClassesDir = project.layout.buildDirectory
            .dir("classes/atomicfu/${target.name}/${compilation.name}")
        val transformTask = when (target.platformType) {
            KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> {
                // create transformation task only if transformation is required and JVM IR compiler transformation is not enabled
                if (config.transformJvm && !needsJvmIrTransformation(target)) {
                    project.registerJvmTransformTask(compilation)
                        .configureJvmTask(
                            compilation.compileDependencyFiles,
                            compilation.compileAllTaskName,
                            transformedClassesDir,
                            originalClassesDirs,
                            config
                        )
                        .also {
                            compilation.defaultSourceSet.kotlin.compiledBy(it, AtomicFUTransformTask::destinationDirectory)
                        }
                } else null
            }
            KotlinPlatformType.js -> {
                // create transformation task only if transformation is required and JS IR compiler transformation is not enabled
                if (config.transformJs && !needsJsIrTransformation(target)) {
                    project.registerJsTransformTask(compilation)
                        .configureJsTask(
                            compilation.compileAllTaskName,
                            transformedClassesDir,
                            originalClassesDirs,
                            config
                        )
                        .also {
                            compilation.defaultSourceSet.kotlin.compiledBy(it, AtomicFUTransformJsTask::destinationDirectory)
                        }
                } else null
            }
            else -> error("Unsupported transformation platform '${target.platformType}'")
        }
        if (transformTask != null) {
            //now transformTask is responsible for compiling this source set into the classes directory
            compilation.defaultSourceSet.kotlin.destinationDirectory.value(transformedClassesDir)
            classesDirs.setFrom(transformedClassesDir)
            classesDirs.setBuiltBy(listOf(transformTask))
            tasks.withType(Jar::class.java).configureEach {
                if (name == target.artifactsTaskName) {
                    it.setupJarManifest(multiRelease = config.jvmVariant.toJvmVariant() == JvmVariant.BOTH)
                }
            }
        }
        // test should compile and run against original production binaries
        if (compilationType == CompilationType.TEST) {
            val mainCompilation =
                compilation.target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
            val originalMainClassesDirs = project.objects.fileCollection().from(
                mainCompilation.compileTaskProvider.flatMap { (it as KotlinCompileTool).destinationDirectory }
            )
            // compilationTask.destinationDirectory was changed from build/classes/kotlin/main to build/classes/atomicfu-orig/main,
            // so we need to update libraries
            (tasks.findByName(compilation.compileKotlinTaskName) as? AbstractKotlinCompileTool<*>)
                ?.libraries
                ?.setFrom(
                    originalMainClassesDirs + compilation.compileDependencyFiles
                )
            if (transformTask != null) {
                // if transform task was not created, then originalMainClassesDirs == mainCompilation.output.classesDirs
                (tasks.findByName("${target.name}${compilation.name.capitalize()}") as? Test)?.classpath =
                    originalMainClassesDirs + (compilation as KotlinCompilationToRunnableFiles).runtimeDependencyFiles - mainCompilation.output.classesDirs
            }
            compilation.compileKotlinTask.setFriendPaths(originalMainClassesDirs)
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
    if (rootProject.getBooleanProperty("kotlin.mpp.enableGranularSourceSetsMetadata")) {
        addCompilerPluginDependency()
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
            addCompilerPluginDependency()
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
                        KotlinPlatformType.native, KotlinPlatformType.wasm -> Platform.NATIVE
                    }

            val configurationName = when {
                // impl dependency for native when IR transformation is off
                platform == Platform.NATIVE && !project.getBooleanProperty(ENABLE_NATIVE_IR_TRANSFORMATION) -> sourceSet.implementationConfigurationName
                // compileOnly dependency for main compilation (commonMain, jvmMain, jsMain, nativeMain)
                compilationType == CompilationType.MAIN -> sourceSet.compileOnlyConfigurationName
                // impl dependency for tests
                else -> sourceSet.implementationConfigurationName
            }
            dependencies.add(configurationName, getAtomicfuDependencyNotation(platform, version))
        }
    }
}

fun String.toJvmVariant(): JvmVariant = enumValueOf(toUpperCase(Locale.US))

fun Project.registerJvmTransformTask(compilation: KotlinCompilation<*>): TaskProvider<AtomicFUTransformTask> =
    tasks.register(
        "transform${compilation.target.name.capitalize()}${compilation.name.capitalize()}Atomicfu",
        AtomicFUTransformTask::class.java
    )

fun Project.registerJsTransformTask(compilation: KotlinCompilation<*>): TaskProvider<AtomicFUTransformJsTask> =
    tasks.register(
        "transform${compilation.target.name.capitalize()}${compilation.name.capitalize()}Atomicfu",
        AtomicFUTransformJsTask::class.java
    )

fun TaskProvider<AtomicFUTransformTask>.configureJvmTask(
    classpath: FileCollection,
    classesTaskName: String,
    transformedClassesDir: Provider<Directory>,
    originalClassesDir: FileCollection,
    config: AtomicFUPluginExtension
): TaskProvider<AtomicFUTransformTask> =
    apply {
        configure {
            it.dependsOn(classesTaskName)
            it.classPath = classpath
            it.inputFiles = originalClassesDir
            it.destinationDirectory.value(transformedClassesDir)
            it.jvmVariant = config.jvmVariant
            it.verbose = config.verbose
        }
    }

fun TaskProvider<AtomicFUTransformJsTask>.configureJsTask(
    classesTaskName: String,
    transformedClassesDir: Provider<Directory>,
    originalClassesDir: FileCollection,
    config: AtomicFUPluginExtension
): TaskProvider<AtomicFUTransformJsTask> =
    apply {
        configure {
            it.dependsOn(classesTaskName)
            it.inputFiles = originalClassesDir
            it.destinationDirectory.value(transformedClassesDir)
            it.verbose = config.verbose
        }
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
    var jvmVariant: String = "FU"
    var verbose: Boolean = false
}

@CacheableTask
abstract class AtomicFUTransformTask : ConventionTask() {
    @get:Inject
    internal abstract val providerFactory: ProviderFactory

    @get:Inject
    internal abstract val projectLayout: ProjectLayout

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    lateinit var inputFiles: FileCollection

    @Suppress("unused")
    @Deprecated(
        message = "Replaced with 'destinationDirectory'",
        replaceWith = ReplaceWith("destinationDirectory")
    )
    @get:Internal
    var outputDir: File
        get() = destinationDirectory.get().asFile
        set(value) { destinationDirectory.value(projectLayout.dir(providerFactory.provider { value })) }

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @Classpath
    @InputFiles
    lateinit var classPath: FileCollection

    @Input
    var jvmVariant = "FU"

    @Input
    var verbose = false

    @TaskAction
    fun transform() {
        val cp = classPath.files.map { it.absolutePath }
        inputFiles.files.forEach { inputDir ->
            AtomicFUTransformer(cp, inputDir, destinationDirectory.get().asFile).let { t ->
                t.jvmVariant = jvmVariant.toJvmVariant()
                t.verbose = verbose
                t.transform()
            }
        }
    }
}

@CacheableTask
abstract class AtomicFUTransformJsTask : ConventionTask() {

    @get:Inject
    internal abstract val providerFactory: ProviderFactory

    @get:Inject
    internal abstract val projectLayout: ProjectLayout

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    lateinit var inputFiles: FileCollection

    @Suppress("unused")
    @Deprecated(
        message = "Replaced with 'destinationDirectory'",
        replaceWith = ReplaceWith("destinationDirectory")
    )
    @get:Internal
    var outputDir: File
        get() = destinationDirectory.get().asFile
        set(value) { destinationDirectory.value(projectLayout.dir(providerFactory.provider { value })) }

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @Input
    var verbose = false

    @TaskAction
    fun transform() {
        inputFiles.files.forEach { inputDir ->
            AtomicFUTransformerJS(inputDir, destinationDirectory.get().asFile).let { t ->
                t.verbose = verbose
                t.transform()
            }
        }
    }
}
