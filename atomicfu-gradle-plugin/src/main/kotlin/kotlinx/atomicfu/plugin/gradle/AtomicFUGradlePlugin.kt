package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.AtomicFUTransformer
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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
    @InputDirectory
    lateinit var inputDir: File
    @OutputDirectory
    lateinit var outputDir: File
    @Input
    var classPath: List<String> = listOf()
    @Input
    var verbose: Boolean = false

    @TaskAction
    fun transform() {
        val t = AtomicFUTransformer(classPath, inputDir, outputDir)
        t.verbose = verbose
        t.transform()
    }
}