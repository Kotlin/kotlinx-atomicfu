package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.AtomicFUTransformer
import kotlinx.atomicfu.transformer.Variant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import java.io.File
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*


open class AtomicFUGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureTransformation()
    }
}

fun Project.configureTransformation() {
    afterEvaluate {
        println("AFTER EVALUATE STAGE STARTED")
        sourceSets.all { sourceSetParam ->
            val classesDirs = (sourceSetParam.output.classesDirs as ConfigurableFileCollection).from as Collection<Any>

            //make copy of original classes directory
            val classesDirsCopy = project.files(classesDirs.toTypedArray()).filter { it.exists() }
            println("\t CLASSESDIRSCOPY: ")
            classesDirsCopy.files.forEach{print(it.path)}
            println()

            //directory for transformed classes
            val transformedClassesDir = File(project.buildDir, "classes/${sourceSetParam.name}-instrumented")
            // make transformedClassesDir the source path for output.classesDirs
            (sourceSetParam.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)

            println("\t SOURCESET after set & before transform: ")
            sourceSetParam.output.classesDirs.files.forEach{print(it.path)}
            println()

            val instrumentTask = project.tasks.create(sourceSetParam.getTaskName("instrument", "classes"), AtomicFUTransformTask::class.java)
            instrumentTask.apply {
                dependsOn(sourceSetParam.classesTaskName).onlyIf { !classesDirsCopy.isEmpty }
                inputFiles = classesDirsCopy
                outputDir = transformedClassesDir
            }
            instrumentTask.outputs.dir(transformedClassesDir)

            //now instrumentTask is responsible for compiling this source set into the classes directory
            sourceSetParam.compiledBy(instrumentTask)
        }
    }
}

val Project.sourceSets: SourceSetContainer
    get() = convention.getPlugin(JavaPluginConvention::class.java).sourceSets


@CacheableTask
open class AtomicFUTransformTask : ConventionTask() {
    @InputFiles
    lateinit var inputFiles: FileCollection
    @OutputDirectory
    lateinit var outputDir: File
    @InputFiles
    var classPath: FileCollection = project.files()
    @Input
    var verbose = false
    @Input
    var variant= Variant.BOTH

    @TaskAction
    fun transform() {
        println("ENTERED TRANSFORM FUNCTION")
        inputFiles.files.forEach {
            AtomicFUTransformer(classPath.files.map { it.absolutePath }, it).let { t ->
                t.outputDir = outputDir
                t.variant = variant
                t.verbose = verbose
                println("AND NOW TRANSFORM TASK")
                t.transform()
            }
        }
    }
}