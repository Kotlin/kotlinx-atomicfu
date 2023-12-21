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
    
    @Test
    fun testResolvedCompilerPluginDependency() {
        val buildDependencies = pluginOrderBugProject.buildEnvironment()
        val classpath = pluginOrderBugProject.getProjectClasspath()
        val kpg = classpath.firstOrNull { it.startsWith("org.jetbrains.kotlin:kotlin-gradle-plugin") } 
            ?: error("kotlin-gradle-plugin is not found in the classpath of the project ${pluginOrderBugProject.projectName}")
        val kgpVersion = kpg.substringAfterLast(":")
        assertTrue(classpath.contains("org.jetbrains.kotlin:atomicfu:$kgpVersion"))
    }
}
