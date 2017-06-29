package org.jetbrains.atomicfu.plugin

import org.apache.maven.plugin.AbstractMojo

import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.jetbrains.atomicfu.AtomicFUTransformer

import java.io.File

@Mojo(name = "transform", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
class TransformMojo : AbstractMojo() {
    /**
     * Location of the file.
     */
    @Parameter(defaultValue = "\${project.build.outputDirectory}", property = "outputDir", required = true)
    lateinit var outputDirectory: File

    override fun execute() {
        AtomicFUTransformer(outputDirectory).transform()
    }
}
