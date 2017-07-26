package kotlinx.atomicfu.plugin

import kotlinx.atomicfu.transformer.AtomicFUTransformer
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

@Mojo(name = "transform", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
class TransformMojo : AbstractMojo() {
    /**
     * Location of the file.
     */
    @Parameter(defaultValue = "\${project.build.outputDirectory}", property = "outputDir", required = true)
    lateinit var outputDir: File

    /**
     * Verbose debug info.
     */
    @Parameter(defaultValue = "false", property = "verbose", required = false)
    var verbose: Boolean = false

    override fun execute() {
        val t = AtomicFUTransformer(outputDir)
        t.verbose = verbose
        t.transform()
    }
}
