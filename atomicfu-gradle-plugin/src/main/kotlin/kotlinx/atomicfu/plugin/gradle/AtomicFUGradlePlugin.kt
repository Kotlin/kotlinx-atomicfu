package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.file.*
import org.gradle.api.internal.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.*
import org.gradle.jvm.tasks.*
import java.io.*
import java.util.*

private const val EXTENSION_NAME = "atomicfu"
private const val ORIGINAL_DIR_NAME = "originalClassesDir"

open class AtomicFUGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add(EXTENSION_NAME, AtomicFUPluginExtension())
        project.configureOriginalDirTest()
        project.configureTransformTasks()
    }
}

fun Project.configureOriginalDirTest() {
    plugins.matching { it::class.java.canonicalName.startsWith("org.jetbrains.kotlin.gradle.plugin") }.all {
        val compileTestKotlin = tasks.findByName("compileTestKotlin") as AbstractCompile?
        compileTestKotlin?.doFirst {
            compileTestKotlin.classpath = (compileTestKotlin.classpath
                - mainSourceSet.output.classesDirs
                + files((mainSourceSet as ExtensionAware).extensions.getByName(ORIGINAL_DIR_NAME)))
        }
        val compileTestKotlin2Js = tasks.findByName("compileTestKotlin2Js") as AbstractCompile?
        compileTestKotlin2Js?.doFirst {
            compileTestKotlin2Js.classpath = (compileTestKotlin2Js.classpath
                - mainSourceSet.output.classesDirs
                + files((mainSourceSet as ExtensionAware).extensions.getByName(ORIGINAL_DIR_NAME)))
        }
    }
}

fun Project.configureTransformTasks() {
    afterEvaluate {
        val jvmTarget = pluginManager.hasPlugin("kotlin-platform-jvm") || pluginManager.hasPlugin("kotlin")
        val jsTarget = pluginManager.hasPlugin("kotlin-platform-js") || pluginManager.hasPlugin("kotlin2js")
        val config = extensions.findByName(EXTENSION_NAME) as? AtomicFUPluginExtension
        sourceSets.all { sourceSetParam ->
            val classesDirs = (sourceSetParam.output.classesDirs as ConfigurableFileCollection).from as Collection<Any>
            // make copy of original classes directory
            val originalClassesDir = project.files(classesDirs.toTypedArray()).filter { it.exists() }
            (sourceSetParam as ExtensionAware).extensions.add(ORIGINAL_DIR_NAME, originalClassesDir)
            val transformedClassesDir = File(project.buildDir, "classes${File.separatorChar}${sourceSetParam.name}-atomicfu")
            // make transformedClassesDir the source path for output.classesDirs
            (sourceSetParam.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)
            val transformTask = when {
                jvmTarget -> configureJvmTask(sourceSetParam, transformedClassesDir, originalClassesDir, config)
                jsTarget -> configureJsTask(sourceSetParam, transformedClassesDir, originalClassesDir, config)
                else -> error("AtomicFUGradlePlugin can be applied to Kotlin/JVM or Kotlin/JS project. " +
                    "The corresponding plugins were not detected.")
            }
            //now transformTask is responsible for compiling this source set into the classes directory
            sourceSetParam.compiledBy(transformTask)
        }
        (tasks.findByName("jar") as? Jar)?.setupJarManifest(multiRelease = config?.variant?.toVariant() == Variant.BOTH)
    }
}

fun String.toVariant(): Variant = enumValueOf(toUpperCase(Locale.US))

fun Project.configureJvmTask(
    sourceSetParam: SourceSet,
    transformedClassesDir: File,
    originalClassesDir: FileCollection,
    config: AtomicFUPluginExtension?
): ConventionTask {
    val transformTask = project.tasks.create(
        sourceSetParam.getTaskName("transform", "atomicfuClasses"),
        AtomicFUTransformTask::class.java
    )
    transformTask.apply {
        dependsOn(sourceSetParam.classesTaskName)
        sourceSet = sourceSetParam
        inputFiles = originalClassesDir
        outputDir = transformedClassesDir
        config?.let {
            variant = it.variant
            verbose = it.verbose
        }
    }
    return transformTask
}

fun Project.configureJsTask(
    sourceSetParam: SourceSet,
    transformedClassesDir: File,
    originalClassesDir: FileCollection,
    config: AtomicFUPluginExtension?
): ConventionTask {
    val transformTask = project.tasks.create(
        sourceSetParam.getTaskName("transform", "atomicfuJsFiles"),
        AtomicFUTransformJsTask::class.java
    )
    transformTask.apply {
        dependsOn(sourceSetParam.classesTaskName)
        sourceSet = sourceSetParam
        inputFiles = originalClassesDir
        outputDir = transformedClassesDir
        config?.let {
            verbose = it.verbose
        }
    }
    return transformTask
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

val Project.mainSourceSet: SourceSet
    get() = sourceSets.getByName("main")

class AtomicFUPluginExtension {
    var variant: String = "FU"
    var verbose: Boolean = false
}

@CacheableTask
open class AtomicFUTransformTask : ConventionTask() {
    var sourceSet: SourceSet? = null

    @InputFiles
    lateinit var inputFiles: FileCollection
    @OutputDirectory
    lateinit var outputDir: File
    @InputFiles
    var classPath: FileCollection = project.files()
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
    var sourceSet: SourceSet? = null

    @InputFiles
    lateinit var inputFiles: FileCollection
    @OutputFile
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