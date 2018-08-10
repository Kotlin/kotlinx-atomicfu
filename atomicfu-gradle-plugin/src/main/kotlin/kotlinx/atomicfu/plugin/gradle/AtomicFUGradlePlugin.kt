package kotlinx.atomicfu.plugin.gradle

import kotlinx.atomicfu.transformer.AtomicFUTransformer
import kotlinx.atomicfu.transformer.Variant
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import java.io.File
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.jvm.tasks.Jar

open class AtomicFUGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureTransformation()
        (target.tasks.findByName("jar") as Jar).setupJarManifest()
    }
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
    afterEvaluate {
        sourceSets.all { sourceSetParam ->
            val classesDirs = (sourceSetParam.output.classesDirs as ConfigurableFileCollection).from as Collection<Any>

            // make copy of original classes directory
            val classesDirsCopy = project.files(classesDirs.toTypedArray()).filter { it.exists() }
            (sourceSetParam as ExtensionAware).extensions.add("classesDirsCopy", classesDirsCopy)

            // directory for transformed classes
            val transformedClassesDir = File(project.buildDir, "classes/${sourceSetParam.name}-transformed")
            // make transformedClassesDir the source path for output.classesDirs
            (sourceSetParam.output.classesDirs as ConfigurableFileCollection).setFrom(transformedClassesDir)

            val transformTask = project.tasks.create(sourceSetParam.getTaskName("transform", "classes"), AtomicFUTransformTask::class.java)
            transformTask.apply {
                dependsOn(sourceSetParam.classesTaskName).onlyIf { !classesDirsCopy.isEmpty }
                sourceSet = sourceSetParam
                inputFiles = classesDirsCopy
                outputDir = transformedClassesDir
            }
            transformTask.outputs.dir(transformedClassesDir)

            //now instrumentTask is responsible for compiling this source set into the classes directory
            sourceSetParam.compiledBy(transformTask)
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
    var variant= if (JavaVersion.current().ordinal >= JavaVersion.VERSION_1_9.ordinal) Variant.VH else Variant.BOTH

    @TaskAction
    fun transform() {
        val dependenciesSourceDirs = project.configurations.getByName("compile").dependencies.withType(ProjectDependency::class.java)
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