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
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.*
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationToRunnableFiles
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMultiplatformPlugin
import java.io.*
import java.util.*
import java.util.concurrent.Callable

private const val EXTENSION_NAME = "atomicfu"
private const val ORIGINAL_DIR_NAME = "originalClassesDir"

open class AtomicFUGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add(EXTENSION_NAME, AtomicFUPluginExtension())
        project.withPlugins("kotlin") {
            configureTransformTasks("compileTestKotlin") { sourceSet, transformedDir, originalDir, config ->
                createJvmTransformTask(sourceSet).configureJvmTask(sourceSet.compileClasspath, sourceSet.classesTaskName, transformedDir, originalDir, config)
            }
        }
        project.withPlugins("kotlin2js") {
            configureTransformTasks("compileTestKotlin2Js") { sourceSet, transformedDir, originalDir, config ->
                createJsTransformTask(sourceSet).configureJsTask(sourceSet.classesTaskName, transformedDir, originalDir, config)
            }
        }
        project.withPlugins("kotlin-multiplatform") {
            afterEvaluate {
                configureMultiplatformPlugin()
            }
        }
    }
}

fun Project.withPlugins(vararg plugins: String, fn: Project.() -> Unit) {
    plugins.forEach { pluginManager.withPlugin(it) { fn() } }
}

fun Project.configureMultiplatformPlugin() {
    val originalDirsByCompilation = hashMapOf<KotlinCompilation<*>, FileCollection>()
    project.extensions.findByType(KotlinProjectExtension::class.java)?.let { kotlinExtension ->
        val config = extensions.findByName(EXTENSION_NAME) as? AtomicFUPluginExtension

        val targetsExtension = (kotlinExtension as? ExtensionAware)?.extensions?.findByName("targets")
        @Suppress("UNCHECKED_CAST")
        val targets = targetsExtension as NamedDomainObjectContainer<KotlinTarget>
        targets.all { target ->
            if (target.name == KotlinMultiplatformPlugin.METADATA_TARGET_NAME) {
                return@all // skip the metadata targets
            }

            target.compilations.all { compilation ->
                val classesDirs = compilation.output.classesDirs as ConfigurableFileCollection
                // make copy of original classes directory
                val originalClassesDirs: FileCollection = project.files(classesDirs.from.toTypedArray()).filter { it.exists() }
                originalDirsByCompilation[compilation] = originalClassesDirs

                val transformedClassesDir = project.buildDir.resolve("classes/atomicfu/${target.name}/${compilation.name}")
                // make transformedClassesDir the source path for output.classesDirs
                classesDirs.setFrom(transformedClassesDir)
                val transformTask = when (target.platformType) {
                    KotlinPlatformType.jvm -> {
                        project.createJvmTransformTask(compilation).configureJvmTask(compilation.compileDependencyFiles, compilation.compileAllTaskName, transformedClassesDir, originalClassesDirs, config)
                    }
                    KotlinPlatformType.js -> {
                        project.createJsTransformTask(compilation).configureJsTask(compilation.compileAllTaskName, transformedClassesDir, originalClassesDirs, config)
                    }
                    else -> {
                        // todo KotlinPlatformType.android?
                        return@all
                    }
                }
                //now transformTask is responsible for compiling this source set into the classes directory
                classesDirs.builtBy(transformTask)
                (tasks.findByName(target.artifactsTaskName) as? Jar)?.apply {
                    setupJarManifest(multiRelease = config?.variant?.toVariant() == Variant.BOTH)
                }

                if (compilation.name == KotlinCompilation.TEST_COMPILATION_NAME) {
                    // test should compile and run against original production binaries
                    val mainCompilation = compilation.target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
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
}

fun Project.configureTransformTasks(
    testTaskName: String,
    createTransformTask: (sourceSet: SourceSet, transformedDir: File, originalDir: FileCollection, config: AtomicFUPluginExtension?) -> Task
) {
    afterEvaluate {
        sourceSets.all { sourceSetParam ->
            val config = extensions.findByName(EXTENSION_NAME) as? AtomicFUPluginExtension
            val classesDirs = (sourceSetParam.output.classesDirs as ConfigurableFileCollection).from as Collection<Any>
            // make copy of original classes directory
            val originalClassesDirs: FileCollection = project.files(classesDirs.toTypedArray()).filter { it.exists() }
            (sourceSetParam as ExtensionAware).extensions.add(ORIGINAL_DIR_NAME, originalClassesDirs)
            val transformedClassesDir = File(project.buildDir, "classes${File.separatorChar}${sourceSetParam.name}-atomicfu")
            // make transformedClassesDir the source path for output.classesDirs
            (sourceSetParam.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)
            val transformTask = createTransformTask(sourceSetParam, transformedClassesDir, originalClassesDirs, config)
            //now transformTask is responsible for compiling this source set into the classes directory
            sourceSetParam.compiledBy(transformTask)
            (tasks.findByName(sourceSetParam.jarTaskName) as? Jar)?.apply {
                setupJarManifest(multiRelease = config?.variant?.toVariant() == Variant.BOTH)
            }

            if (sourceSetParam.name == SourceSet.TEST_SOURCE_SET_NAME) {
                // test should compile and run against original production binaries
                val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                val originalMainClassesDirs = project.files(
                    // use Callable because there is no guarantee that main is configured before test
                    Callable { (mainSourceSet as ExtensionAware).extensions.getByName(ORIGINAL_DIR_NAME) as FileCollection }
                )

                (tasks.findByName(testTaskName) as? AbstractCompile)?.classpath =
                        originalMainClassesDirs + sourceSetParam.compileClasspath - mainSourceSet.output.classesDirs

                // todo: fix test runtime classpath for JS?
                (tasks.findByName(JavaPlugin.TEST_TASK_NAME) as? Test)?.classpath =
                        originalMainClassesDirs + sourceSetParam.runtimeClasspath - mainSourceSet.output.classesDirs
            }
        }
    }
}

fun String.toVariant(): Variant = enumValueOf(toUpperCase(Locale.US))

fun Project.createJvmTransformTask(compilation: KotlinCompilation<*>) =
    tasks.create("transform${compilation.target.name.capitalize()}${compilation.name.capitalize()}Atomicfu", AtomicFUTransformTask::class.java)

fun Project.createJsTransformTask(compilation: KotlinCompilation<*>) =
    tasks.create("transform${compilation.target.name.capitalize()}${compilation.name.capitalize()}Atomicfu", AtomicFUTransformJsTask::class.java)

fun Project.createJvmTransformTask(sourceSet: SourceSet) =
    tasks.create(sourceSet.getTaskName("transform", "atomicfuClasses"), AtomicFUTransformTask::class.java)

fun Project.createJsTransformTask(sourceSet: SourceSet) =
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