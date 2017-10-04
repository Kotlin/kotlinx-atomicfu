package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.AtomicFUTransformer
import kotlinx.atomicfu.transformer.Variant
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

open class AtomicFUGradlePlugin : Plugin<Project> {
    private val TASK_NAME = "atomicFU"
    override fun apply(target: Project) {
        target.tasks.create(TASK_NAME, AtomicFUTransformTask::class.java)
    }
}

open class AtomicFUTransformTask : DefaultTask() {

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
        inputFiles.files.forEach {
            val t = AtomicFUTransformer(
                classPath.files.map { it.absolutePath }, it, outputDir, variant)
            t.verbose = verbose
            t.transform()
        }
    }
}