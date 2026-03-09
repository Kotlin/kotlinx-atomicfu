/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.*
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.*
import org.gradle.jvm.tasks.*
import org.gradle.workers.*
import org.gradle.util.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.tasks.*
import java.io.*
import java.util.*
import java.util.Locale
import javax.inject.Inject

private const val EXTENSION_NAME = "atomicfu"
private const val COMPILE_ONLY_CONFIGURATION = "compileOnly"
private const val IMPLEMENTATION_CONFIGURATION = "implementation"
private const val TEST_IMPLEMENTATION_CONFIGURATION = "testImplementation"

// If the project uses KGP <= 1.6.20, only JS IR compiler plugin is available, and it is turned on via setting this property.
// The property is supported for backwards compatibility.
private const val ENABLE_JS_IR_TRANSFORMATION_LEGACY = "kotlinx.atomicfu.enableIrTransformation"
internal const val ENABLE_JS_IR_TRANSFORMATION = "kotlinx.atomicfu.enableJsIrTransformation"
internal const val ENABLE_JVM_IR_TRANSFORMATION = "kotlinx.atomicfu.enableJvmIrTransformation"
internal const val ENABLE_NATIVE_IR_TRANSFORMATION = "kotlinx.atomicfu.enableNativeIrTransformation"
private const val MIN_SUPPORTED_GRADLE_VERSION = "8.2"
private const val MIN_SUPPORTED_KGP_VERSION = "1.7.0"

private const val ATOMICFU_TRANSFORMER_CLASSPATH_RESOLVER_ID = "atomicfu.transformer.classpath.resolver"

open class AtomicFUGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) = project.run {
        // Atomicfu version is stored at build time in atomicfu.properties file
        // located in atomicfu-gradle-plugin resources
        val afuPluginVersion = loadPropertyFromResources("atomicfu.properties", "atomicfu.version")
        checkCompatibility(afuPluginVersion)
        extensions.add(EXTENSION_NAME, AtomicFUPluginExtension(afuPluginVersion))
        // Apply Atomicfu compiler plugin
        plugins.apply(AtomicfuKotlinCompilerPluginInternal::class.java)
        configureDependencies()
        configureTasks()
    }
}

private fun loadPropertyFromResources(propFileName: String, property: String): String {
    val props = Properties()
    val inputStream = AtomicFUGradlePlugin::class.java.classLoader!!.getResourceAsStream(propFileName)
        ?: throw FileNotFoundException(
            "You are applying `kotlinx-atomicfu` plugin of version 0.24.0 or newer, yet we were unable to determine the specific version of the plugin.\". \n" +
                "Starting from version 0.24.0 of `kotlinx-atomicfu`, the plugin version is extracted from the `atomicfu.properties` file, which resides within the atomicfu-gradle-plugin-{version}.jar. \n" +
                "However, this file couldn't be found. Please ensure that there are no atomicfu-gradle-plugin-{version}.jar with version older than 0.24.0 present on the classpath.\n" +
                "If the problem is not resolved, please submit the issue: https://github.com/Kotlin/kotlinx-atomicfu/issues"
        )
    inputStream.use { props.load(it) }
    return props[property] as String
}

private fun Project.checkCompatibility(afuPluginVersion: String) {
    val currentGradleVersion = GradleVersion.current()
    val kotlinVersion = getKotlinVersion()
    val minSupportedVersion = GradleVersion.version(MIN_SUPPORTED_GRADLE_VERSION)
    if (currentGradleVersion < minSupportedVersion) {
        throw GradleException(
            "The current Gradle version is not compatible with Atomicfu gradle plugin. " +
                "Please use Gradle $MIN_SUPPORTED_GRADLE_VERSION or newer, or the previous version of Atomicfu gradle plugin."
        )
    }
    if (!kotlinVersion.atLeast(1, 9, 0)) {
        // Since Kotlin 1.9.0 the logic of the Gradle plugin from the Kotlin repo (AtomicfuKotlinGradleSubplugin)
        // may be moved to the Gradle plugin in the library. The sources of the compiler plugin
        // are published as `kotlin-atomicfu-compiler-plugin-embeddable` since Kotlin 1.9.0 and may be accessed out of the Kotlin repo.
        error(
            "You are applying `kotlinx-atomicfu` plugin of version $afuPluginVersion. " +
                "However, this version of the plugin is only compatible with Kotlin versions newer than 1.9.0.\n" +
                "If you wish to use this version of the plugin, please upgrade your Kotlin version to 1.9.0 or newer.\n" +
                "In case you can not upgrade the Kotlin version, please read further instructions in the README: https://github.com/Kotlin/kotlinx-atomicfu/blob/master/README.md#requirements \n" +
                "If you encounter any problems, please submit the issue: https://github.com/Kotlin/kotlinx-atomicfu/issues"
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
        prepareAtomicfuTransformerClasspath(version)
    }
    withPluginWhenEvaluatedDependencies("org.jetbrains.kotlin.js") { version ->
        dependencies.add(
            if (needsJsIrTransformation(KotlinPlatformType.js)) COMPILE_ONLY_CONFIGURATION else IMPLEMENTATION_CONFIGURATION,
            getAtomicfuDependencyNotation(Platform.JS, version)
        )
        dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION, getAtomicfuDependencyNotation(Platform.JS, version))
        addJsCompilerPluginRuntimeDependency()
    }
    withPluginWhenEvaluatedDependencies("kotlin-multiplatform") { version ->
        addJsCompilerPluginRuntimeDependency()
        configureMultiplatformPluginDependencies(version)
        prepareAtomicfuTransformerClasspath(version)
    }
}

private fun Project.configureMultiplatformPluginDependencies(version: String) {
    val multiplatformExtension =
        kotlinExtension as? KotlinMultiplatformExtension ?: error("Expected kotlin multiplatform extension")
    val atomicfuDependency = "org.jetbrains.kotlinx:atomicfu:$version"
    multiplatformExtension.sourceSets.getByName("commonMain").dependencies {
        compileOnly(atomicfuDependency)
    }
    multiplatformExtension.sourceSets.getByName("commonTest").dependencies {
        implementation(atomicfuDependency)
    }
    // Include atomicfu as a dependency for publication when transformation for the target is disabled
    multiplatformExtension.targets
        .matching { target -> isTransitiveAtomicfuDependencyRequired(target) }
        .all { target ->
            // Add an implementation dependency for native/wasm targets or if transformation is disable
            target.compilations.all { compilation ->
                compilation
                    .defaultSourceSet
                    .dependencies {
                        implementation(atomicfuDependency)
                    }
            }
        }
    // atomicfu should also appear in apiElements config for Kotlin/Native, JS and WASM targets,
    // otherwise the warning is triggered, see: KT-64109
    multiplatformExtension.targets
        .matching { target ->
            target.platformType == KotlinPlatformType.native ||
            target.platformType == KotlinPlatformType.wasm ||
            target.platformType == KotlinPlatformType.js
        }
        .all { target ->
            target.compilations.all { compilation ->
                compilation
                    .defaultSourceSet
                    .dependencies {
                        // api dependency does not make a lot of sense for test compilations;
                        // moreover, they are no longer supported there: KT-63285
                        if (compilation.name != "test") {
                            api(atomicfuDependency)
                        }
                    }
            }
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

internal fun Project.getBooleanProperty(name: String) =
    rootProject.findProperty(name)?.toString()?.toBooleanStrict() ?: false

private fun String.toBooleanStrict(): Boolean = when (this) {
    "true" -> true
    "false" -> false
    else -> throw IllegalArgumentException("The string doesn't represent a boolean value: $this")
}

internal fun Project.needsJsIrTransformation(targetPlatformType: KotlinPlatformType): Boolean =
    (rootProject.getBooleanProperty(ENABLE_JS_IR_TRANSFORMATION) || rootProject.getBooleanProperty(ENABLE_JS_IR_TRANSFORMATION_LEGACY))
            && targetPlatformType == KotlinPlatformType.js

internal fun Project.needsJvmIrTransformation(targetPlatformType: KotlinPlatformType): Boolean =
    rootProject.getBooleanProperty(ENABLE_JVM_IR_TRANSFORMATION) &&
        (targetPlatformType == KotlinPlatformType.jvm || targetPlatformType == KotlinPlatformType.androidJvm)

internal fun Project.needsNativeIrTransformation(targetPlatformType: KotlinPlatformType): Boolean =
    rootProject.getBooleanProperty(ENABLE_NATIVE_IR_TRANSFORMATION) &&
        (targetPlatformType == KotlinPlatformType.native)

private fun Project.isTransitiveAtomicfuDependencyRequired(target: KotlinTarget): Boolean {
    val platformType = target.platformType
    return !config.transformJvm && (platformType == KotlinPlatformType.jvm || platformType == KotlinPlatformType.androidJvm) ||
        (!needsJsIrTransformation(platformType) && platformType == KotlinPlatformType.js) ||
        platformType == KotlinPlatformType.wasm ||
        // Always add the transitive atomicfu dependency for native targets, see #379
        platformType == KotlinPlatformType.native
}

// Adds kotlinx-atomicfu-runtime as an implementation dependency to the JS IR target:
// it provides inline methods that replace atomic methods from the library and is needed at runtime.
private fun Project.addJsCompilerPluginRuntimeDependency() {
    if (isCompilerPluginAvailable()) {
        withKotlinTargets { target ->
            if (needsJsIrTransformation(target.platformType)) {
                target.compilations.forEach { kotlinCompilation ->
                    kotlinCompilation.defaultSourceSet.dependencies {
                        if (getKotlinVersion().atLeast(1, 7, 10)) {
                            // since Kotlin 1.7.10 `kotlinx-atomicfu-runtime` is published and should be added directly
                            implementation("org.jetbrains.kotlin:kotlinx-atomicfu-runtime:${getKotlinPluginVersion()}")
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

private fun Project.prepareAtomicfuTransformerClasspath(version: String) {
    // isVisible was deprecated in 9.0 (and the "default" became isVisible=true),
    // and it is scheduled for removal in 10.0.
    fun isVisibleSupported() = GradleVersion.current() < GradleVersion.version("9.0")

    val configName = "atomicfu.transformer.classpath"
    val dependencyConfiguration =
        project.configurations.create(configName) {
            it.description = "Runtime classpath for running atomicfu classfile transformation."
            it.isCanBeResolved = false
            it.isCanBeConsumed = false
            if (isVisibleSupported()) {
                it.isVisible = false
            }
        }

    project.dependencies.add(configName, "org.jetbrains.kotlinx:atomicfu-transformer:$version")
    project.dependencies.add(configName, "org.jetbrains.kotlin:kotlin-metadata-jvm:${getKotlinPluginVersion()}")

    project.configurations.register(ATOMICFU_TRANSFORMER_CLASSPATH_RESOLVER_ID) {
        it.description = "Resolve the runtime classpath for running atomicfu classfile transformation."
        it.isCanBeResolved = true
        it.isCanBeConsumed = false
        if (isVisibleSupported()) {
            it.isVisible = false
        }
        it.extendsFrom(dependencyConfiguration)
    }
}

// Note "afterEvaluate" does nothing when the project is already in executed state, so we need
// a special check for this case
private fun <T> Project.whenEvaluated(fn: Project.() -> T) {
    if (state.executed) {
        fn()
    } else {
        afterEvaluate { fn() }
    }
}

private fun Project.withPluginWhenEvaluated(plugin: String, fn: Project.() -> Unit) {
    pluginManager.withPlugin(plugin) { whenEvaluated(fn) }
}

private fun Project.withPluginWhenEvaluatedDependencies(plugin: String, fn: Project.(version: String) -> Unit) {
    withPluginWhenEvaluated(plugin) {
        config.dependenciesVersion?.let { fn(it) }
    }
}

private fun Project.withKotlinTargets(fn: (KotlinTarget) -> Unit) {
    extensions.findByType(KotlinTargetsContainer::class.java)?.let { kotlinExtension ->
        // find all compilations given sourceSet belongs to
        kotlinExtension.targets
            .all { target -> fn(target) }
    }
}

private fun KotlinCompilationTask<*>.setFriendPaths(friendPathsFileCollection: FileCollection) {
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

private fun Project.configureTasks() {
    val config = config
    withPluginWhenEvaluated("kotlin") {
        if (config.transformJvm) configureJvmTransformation()
    }
    withPluginWhenEvaluated("kotlin-multiplatform") {
        configureMultiplatformTransformation()
    }
}

private fun Project.configureJvmTransformation() {
    if (kotlinExtension is KotlinJvmProjectExtension || kotlinExtension is KotlinAndroidProjectExtension) {
        val target = (kotlinExtension as KotlinSingleTargetExtension<*>).target
        if (!needsJvmIrTransformation(target.platformType)) {
            configureTransformationForTarget(target)
        }
    }
}

private fun Project.configureMultiplatformTransformation() =
    withKotlinTargets { target ->
        // Skip transformation for common, native and wasm and js targets or in case IR transformation by the compiler plugin is enabled (for JVM or JS targets)
        if (target.platformType == KotlinPlatformType.common ||
            target.platformType == KotlinPlatformType.native ||
            target.platformType == KotlinPlatformType.wasm ||
            target.platformType == KotlinPlatformType.js ||
            needsJvmIrTransformation(target.platformType) || needsJsIrTransformation(target.platformType)
        ) {
            return@withKotlinTargets
        }
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
                @Suppress("INVISIBLE_MEMBER")
                it.destinationDirectory.value(originalDestinationDirectory)
            } else {
                it.destinationDirectory.value(originalDestinationDirectory)
            }
        }
        val originalClassesDirs: FileCollection = project.objects.fileCollection()
            .from(compilationTask.flatMap { it.destinationDirectory })
            .from({ project.files(classesDirs.from).filter { it.exists() } })
        originalDirsByCompilation[compilation] = originalClassesDirs
        val transformedClassesDir = project.layout.buildDirectory
            .dir("classes/atomicfu/${target.name}/${compilation.name}")
        val transformerConfiguration = project.configurations.getByName(ATOMICFU_TRANSFORMER_CLASSPATH_RESOLVER_ID)
        val transformationRuntimeClasspath = project.objects.fileCollection()
            .from(transformerConfiguration)
            .plus(compilation.compileDependencyFiles)

        val transformTask = when (target.platformType) {
            KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> {
                // create a transformation task only if transformation is required and JVM IR compiler transformation is not enabled
                if (config.transformJvm) {
                    project.registerJvmTransformTask(compilation)
                        .configureJvmTask(
                            transformationRuntimeClasspath,
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
                val runtimeDependencyFiles = compilation.runtimeDependencyFiles
                val newClasspath = if (runtimeDependencyFiles != null)
                    originalMainClassesDirs + runtimeDependencyFiles - mainCompilation.output.classesDirs
                else
                    originalMainClassesDirs - mainCompilation.output.classesDirs
                // if a transform task was not created, then originalMainClassesDirs == mainCompilation.output.classesDirs
                (tasks.findByName("${target.name}${compilation.name.capitalizeCompat()}") as? Test)?.classpath =
                    newClasspath
            }
            compilation.compileTaskProvider.configure {
                it.setFriendPaths(originalMainClassesDirs)
            }
        }
    }
}

private fun String.toJvmVariant(): JvmVariant = enumValueOf(uppercase(Locale.US))

private fun String.capitalizeCompat() = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}

private fun Project.registerJvmTransformTask(compilation: KotlinCompilation<*>): TaskProvider<AtomicFUTransformTask> =
    tasks.register(
        "transform${compilation.target.name.capitalizeCompat()}${compilation.name.capitalizeCompat()}Atomicfu",
        AtomicFUTransformTask::class.java
    )

private fun TaskProvider<AtomicFUTransformTask>.configureJvmTask(
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

private fun Jar.setupJarManifest(multiRelease: Boolean) {
    if (multiRelease) {
        manifest.attributes.apply {
            put("Multi-Release", "true")
        }
    }
}

class AtomicFUPluginExtension(pluginVersion: String?) {
    var dependenciesVersion = pluginVersion
    var transformJvm = true

    @Deprecated("This flag was previously used to enable or disable kotlinx-atomicfu transformations of the final *.js files produced by the JS Legacy backend. " +
            "Starting from version 0.26.0 of `kotlinx-atomicfu`, it does not take any effect, is disabled by default and will be removed in the next release. " +
            "Please ensure that this flag is not used in the atomicfu configuration of your project, you can safely remove it.")
    var transformJs = false
    var jvmVariant: String = "FU"
    var verbose: Boolean = false
}

@CacheableTask
abstract class AtomicFUTransformTask() : DefaultTask() {
    @get:Inject
    internal abstract val providerFactory: ProviderFactory

    @get:Inject
    internal abstract val projectLayout: ProjectLayout

    @get:Inject
    internal abstract val executor: WorkerExecutor

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
        val workQueue = executor.classLoaderIsolation {
            it.classpath.from(classPath)
        }
        workQueue.submit(AtomicFUTransformerAction::class.java) {
            it.jvmVariant.set(jvmVariant)
            it.verbose.set(verbose)
            it.classPath.setFrom(classPath)
            it.inputFiles.setFrom(inputFiles)
            it.destinationDirectory.set(destinationDirectory.get())
        }
        workQueue.await()
    }
}

internal abstract class AtomicFUTransformerAction : WorkAction<AtomicFUTransformerAction.Params> {
    internal interface Params : WorkParameters {
        val jvmVariant: Property<String>
        val verbose: Property<Boolean>
        val classPath: ConfigurableFileCollection
        val inputFiles: ConfigurableFileCollection
        val destinationDirectory: DirectoryProperty
    }

    override fun execute() {
        val destinationDirectory = parameters.destinationDirectory.get().asFile
        destinationDirectory.deleteRecursively()

        val classPath = parameters.classPath.files.map { it.absolutePath }

        parameters.inputFiles.files.forEach { inputDir ->
            AtomicFUTransformer(classPath, inputDir, destinationDirectory).let { t ->
                t.jvmVariant = parameters.jvmVariant.get().toJvmVariant()
                t.verbose = parameters.verbose.get()
                t.transform()
            }
        }
    }
}
