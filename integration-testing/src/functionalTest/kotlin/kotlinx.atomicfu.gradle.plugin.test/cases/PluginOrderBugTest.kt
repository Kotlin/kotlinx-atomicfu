package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.getProjectClasspath
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.GradleBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.cleanAndBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.createGradleBuildFromSources
import kotlin.test.*

/**
 * This test reproduces and tracks the issue #384.
 */
class PluginOrderBugTest {
    private val pluginOrderBugProject: GradleBuild = createGradleBuildFromSources("plugin-order-bug")

    @Test
    fun testUserProjectBuild() {
        val buildResult = pluginOrderBugProject.cleanAndBuild()
        assertTrue(buildResult.isSuccessful, buildResult.output)
    }

    /**
     * Ensures that the version of atomicfu compiler plugin in the project's classpath equals the version of KGP used in the project.
     */
    @Test
    fun testResolvedCompilerPluginDependency() {
        val classpath = pluginOrderBugProject.getProjectClasspath()
        assertTrue(classpath.contains("org.jetbrains.kotlin:atomicfu:${pluginOrderBugProject.getKotlinVersion()}"))
    }

    /**
     * kotlin-stdlib is an implementation dependency of :atomicfu module, 
     * because compileOnly dependencies are not applicable for Native targets (#376).
     * 
     * This test ensures that kotlin-stdlib of the Kotlin version used to build kotlinx-atomicfu library is not "required" in the user's project.
     * The user project should use kotlin-stdlib version that is present in it's classpath.
     */
    @Test
    fun testTransitiveKotlinStdlibDependency() {
        val dependencies = pluginOrderBugProject.dependencies()
        assertFalse(dependencies.output.contains("org.jetbrains.kotlin:kotlin-stdlib:{strictly $libraryKotlinVersion}"), 
            "Strict requirement for 'org.jetbrains.kotlin:kotlin-stdlib:{strictly $libraryKotlinVersion}' was found in the project ${pluginOrderBugProject.projectName} dependencies, " +
                    "while Kotlin version used in the project is ${pluginOrderBugProject.getKotlinVersion()}"
        )
    }
}
