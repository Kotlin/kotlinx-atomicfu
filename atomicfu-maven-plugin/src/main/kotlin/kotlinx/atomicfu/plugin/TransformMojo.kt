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
     * Original classes directory.
     */
    @Parameter(defaultValue = "\${project.build.outputDirectory}", property = "input", required = true)
    lateinit var input: File

    /**
     * Transformed classes directory.
     */
    @Parameter(defaultValue = "\${project.build.outputDirectory}", property = "output", required = true)
    lateinit var output: File

    /**
     * Verbose debug info.
     */
    @Parameter(defaultValue = "false", property = "verbose", required = false)
    var verbose: Boolean = false

    override fun execute() {
        val t = AtomicFUTransformer(input, output)
        t.verbose = verbose
        t.transform()
    }
}
