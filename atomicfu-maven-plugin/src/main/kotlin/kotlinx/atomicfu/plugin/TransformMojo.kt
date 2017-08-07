package kotlinx.atomicfu.plugin

import kotlinx.atomicfu.transformer.AtomicFUTransformer
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import java.io.File

@Mojo(name = "transform",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.COMPILE)
class TransformMojo : AbstractMojo() {
    /**
     * Project classpath.
     */
    @Parameter(defaultValue = "\${project.compileClasspathElements}", required = true, readonly = true)
    lateinit var classpath: List<String>

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
        val t = AtomicFUTransformer(classpath, input, output)
        t.verbose = verbose
        t.transform()
    }
}
