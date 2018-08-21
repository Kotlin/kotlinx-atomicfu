package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.*
import org.apache.tools.ant.taskdefs.Java
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.file.*
import org.gradle.api.internal.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.*
import org.gradle.jvm.tasks.*
import java.io.*

const val COMPILE_ONLY_CONFIGURATION = "compileOnly"
const val TEST_RUNTIME_CONFIGURATION = "testRuntime"

open class AtomicFUGradlePlugin : Plugin<Project> {
    lateinit var project: Project
    private val jsTarget: Boolean
        get() = project.pluginManager.hasPlugin("kotlin-platform-js") || project.pluginManager.hasPlugin("kotlin2js")
    private val jvmTarget: Boolean
        get() = project.pluginManager.hasPlugin("kotlin-platform-jvm") || project.pluginManager.hasPlugin("kotlin")

    override fun apply(target: Project) {
        this.project = target
        applyDependencies()
        target.configureTransformation()
        (target.tasks.findByName("jar") as? Jar)?.setupJarManifest()
    }

    private fun applyDependencies() {
        val dependencies = project.dependencies
        val atomicFuPluginVersion = project.extensions.extraProperties.get("atomicfu_version")
        val platform = when {
            jsTarget -> "-js"
            jvmTarget -> ""
            else -> "-native"
        }
        val atomicfu = dependencies.create("org.jetbrains.kotlinx:atomicfu$platform:${atomicFuPluginVersion.toString()}")
        dependencies.add(COMPILE_ONLY_CONFIGURATION, atomicfu)
        dependencies.add(TEST_RUNTIME_CONFIGURATION, atomicfu)
    }

    fun Project.configureTransformation() {
        plugins.matching { it::class.java.canonicalName.startsWith("org.jetbrains.kotlin.gradle.plugin") }.all {
            val compileTestKotlin = tasks.findByName("compileTestKotlin") as AbstractCompile?
            compileTestKotlin?.doFirst {
                compileTestKotlin.classpath = (compileTestKotlin.classpath
                    - mainSourceSet.output.classesDirs
                    + files((mainSourceSet as ExtensionAware).extensions.getByName("classesDirsCopy")))
            }
        }

        sourceSets.all { sourceSetParam ->
            val transformedClassesDir = File(project.buildDir, "classes/${sourceSetParam.name}-transformed")
            if (jvmTarget) {
                val classesDirs = (sourceSetParam.output.classesDirs as ConfigurableFileCollection).from as Collection<Any>
                // make copy of original classes directory
                val classesDirsCopy = project.files(classesDirs.toTypedArray()).filter { it.exists() }
                (sourceSetParam as ExtensionAware).extensions.add("classesDirsCopy", classesDirsCopy)

                // make transformedClassesDir the source path for output.classesDirs
                (sourceSetParam.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)

                val transformJVMTask = project.tasks.create(
                    sourceSetParam.getTaskName("transform", "classes"),
                    AtomicFUTransformTask::class.java
                )
                transformJVMTask.apply {
                    dependsOn(sourceSetParam.classesTaskName).onlyIf { !classesDirsCopy.isEmpty }
                    sourceSet = sourceSetParam
                    inputFiles = classesDirsCopy
                    outputDir = transformedClassesDir
                }
                transformJVMTask.outputs.dir(transformedClassesDir)
                //now transformJVMTask is responsible for compiling this source set into the classes directory
                sourceSetParam.compiledBy(transformJVMTask)
            }
            if (jsTarget) {
                val compileTaskName = sourceSetParam.getCompileTaskName("Kotlin2Js")
                // manually set by user
                val compileTaskOutputFile = tasks.getByName(compileTaskName).outputs.files.filter { it.canonicalPath.endsWith(".js") }
                val copyTask = project.tasks.create(
                    sourceSetParam.getTaskName("copy", "files"),
                    Copy::class.java
                )
                (sourceSetParam.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)

                // copy all files from original compiled directory except the compileTaskOutputFile with all compiled code from the application
                copyTask.apply {
                    dependsOn(compileTaskName)
                    from(tasks.getByName(compileTaskName).outputs.files)
                    exclude(compileTaskOutputFile.asPath.substringAfterLast('/'))
                    into(transformedClassesDir.canonicalPath)
                }
                val transformJSTask = project.tasks.create(
                    sourceSetParam.getTaskName("transformJS", "files"),
                    AtomicFUTransformJSTask::class.java
                )
                val transformedOutputFile = File(transformedClassesDir, "${compileTaskOutputFile.asPath.substringAfterLast('/').substringBeforeLast(".js")}-transformed.js")

                // transform compileTaskOutputFile and write to transformed directory
                transformJSTask.apply {
                    dependsOn(copyTask.name)
                    sourceSet = sourceSetParam
                    inputFiles = compileTaskOutputFile
                    outputDir = transformedOutputFile
                }
                transformJSTask.outputs.file(transformedOutputFile)
                //now transformJSTask is responsible for compiling this source set into the output directory
                sourceSetParam.compiledBy(transformJSTask)
            }
        }
    }
}

fun Jar.setupJarManifest(classifier: String = "") {
    this.classifier = classifier
    manifest.attributes.apply {
        put("Multi-Release", "true")
    }
}

val Project.sourceSets: SourceSetContainer
    get() = convention.getPlugin(JavaPluginConvention::class.java).sourceSets

val Project.mainSourceSet: SourceSet
    get() = sourceSets.getByName("main")


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
    var verbose = false

    @Input
    var variant = Variant.BOTH

    @TaskAction
    fun transform() {
        val dependenciesSourceDirs =
            project.configurations.getByName("compile").dependencies.withType(ProjectDependency::class.java)
                .map { p -> p.dependencyProject.mainSourceSet.allSource.sourceDirectories }

        classPath = dependenciesSourceDirs.fold(sourceSet!!.compileClasspath) { ss, dep -> ss + dep }

        inputFiles.files.forEach {
            AtomicFUTransformer(classPath.files.map { it.absolutePath }, it).let { t ->
                t.outputDir = outputDir
                t.variant = variant
                t.verbose = verbose
                t.transform()
            }
        }
    }
}

@CacheableTask
open class AtomicFUTransformJSTask : ConventionTask() {

    var sourceSet: SourceSet? = null

    @InputFiles
    lateinit var inputFiles: FileCollection

    @OutputFile
    lateinit var outputDir: File

    @InputFiles
    var classPath: FileCollection = project.files()

    @TaskAction
    fun transform() {
        val dependenciesSourceDirs =
            project.configurations.getByName("compile").dependencies.withType(ProjectDependency::class.java)
                .map { p -> p.dependencyProject.mainSourceSet.allSource.sourceDirectories }

        classPath = dependenciesSourceDirs.fold(sourceSet!!.compileClasspath) { ss, dep -> ss + dep }


        inputFiles.files.forEach {
            AtomicFUTransformerJS(it, outputDir).let { t ->
                t.transform()
            }
        }
    }
}