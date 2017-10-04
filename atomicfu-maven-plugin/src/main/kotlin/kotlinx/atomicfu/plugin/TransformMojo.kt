/*
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlinx.atomicfu.plugin

import kotlinx.atomicfu.transformer.AtomicFUTransformer
import kotlinx.atomicfu.transformer.Variant
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
     * Transformation variant: "FU", "VH", or "BOTH".
     */
    @Parameter(defaultValue = "FU", property = "variant", required = true)
    lateinit var variant: Variant

    /**
     * Verbose debug info.
     */
    @Parameter(defaultValue = "false", property = "verbose", required = false)
    var verbose: Boolean = false

    override fun execute() {
        val t = AtomicFUTransformer(classpath, input, output, variant)
        t.verbose = verbose
        t.transform()
    }
}
