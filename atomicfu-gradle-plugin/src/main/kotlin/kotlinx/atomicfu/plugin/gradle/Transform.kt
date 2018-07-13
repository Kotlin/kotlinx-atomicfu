package kotlinx.atomicfu.plugin.gradle

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import java.io.File
import kotlinx.atomicfu.transformer.AtomicFUTransformer
import kotlinx.atomicfu.transformer.Variant
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.compile.AbstractCompile

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

            val instrumentedClassesDir = File(project.buildDir, "classes/${sourceSetParam.name}-instrumented")
            (sourceSetParam.output.classesDirs as ConfigurableFileCollection).setFrom(instrumentedClassesDir)

            println("\t SOURCESET after set & before transform: ")
            sourceSetParam.output.classesDirs.files.forEach{print(it.path)}
            println()

            val instrumentTask = project.tasks.create(sourceSetParam.getTaskName("instrument", "classes"), AtomicFUTransformTask::class.java)
            instrumentTask.apply {
                dependsOn(sourceSetParam.classesTaskName).onlyIf { !classesDirsCopy.isEmpty }
                inputFiles = classesDirsCopy
                outputDir = instrumentedClassesDir
            }
            instrumentTask.outputs.dir(instrumentedClassesDir)

            //Registers a set of tasks which are responsible for compiling this source set into the classes directory
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
    var variant= Variant.FU

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
