package kotlinx.atomicfu.gradle.plugin.test.cases

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
        assertFalse(buildResult.isSuccessful)
        assertTrue(buildResult.output.contains("Unresolved reference: kotlinx"), buildResult.output)
    }
}
