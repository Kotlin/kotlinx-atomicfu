package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.*
import org.gradle.api.*
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

        project.withPlugins("kotlin") {
            configureTransformTasks("compileTestKotlin", this::configureJvmTask)
        }
        project.withPlugins("kotlin2js") {
            configureTransformTasks("compileTestKotlin2Js", this::configureJsTask)
        }
    }
}

fun Project.withPlugins(vararg plugins: String, fn: Project.() -> Unit) {
    plugins.forEach { pluginManager.withPlugin(it) { fn() } }
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
            val originalClassesDir = project.files(classesDirs.toTypedArray()).filter { it.exists() }
            (sourceSetParam as ExtensionAware).extensions.add(ORIGINAL_DIR_NAME, originalClassesDir)
            val transformedClassesDir = File(project.buildDir, "classes${File.separatorChar}${sourceSetParam.name}-atomicfu")
            // make transformedClassesDir the source path for output.classesDirs
            (sourceSetParam.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)
            val transformTask = createTransformTask(sourceSetParam, transformedClassesDir, originalClassesDir, config)
            //now transformTask is responsible for compiling this source set into the classes directory
            sourceSetParam.compiledBy(transformTask)
            (tasks.findByName(sourceSetParam.jarTaskName) as? Jar)?.apply {
                setupJarManifest(multiRelease = config?.variant?.toVariant() == Variant.BOTH)
            }
            if (sourceSetParam.name == SourceSet.TEST_SOURCE_SET_NAME) {
                (tasks.findByName(testTaskName) as? AbstractCompile)?.configureTestCompile()
            }
        }
    }
}

fun AbstractCompile.configureTestCompile() {
    // TODO: modifying classpath in doFirst breaks up-to-date checks
    // TODO: probably won't work correctly when multiple classes dirs are present (i.e. with Java)
    doFirst {
        val mainSourceSet = project.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        classpath = classpath -
                mainSourceSet.output.classesDirs +
                project.files((mainSourceSet as ExtensionAware).extensions.getByName(ORIGINAL_DIR_NAME))
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