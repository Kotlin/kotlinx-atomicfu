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
    private fun getBuild(testName: String): GradleBuild = createGradleBuildFromSources("plugin-order-bug", testName)

    @Test
    fun testUserProjectBuild() {
        getBuild("PluginOrderBugTest_testUserProjectBuild").cleanAndBuild()
    }

    /**
     * kotlin-stdlib is an implementation dependency of :atomicfu module,
     * because compileOnly dependencies are not applicable for Native targets (#376).
     *
     * This test sets Kotlin version in `plugin-order-bug` project that differs from the Kotlin version set for the library.
     * The goal is to check that kotlin-stdlib of the Kotlin version used to build kotlinx-atomicfu library is not "required" in the user's project.
     * The user project should use kotlin-stdlib version that is present in it's classpath.
     */
    @Test
    fun testTransitiveKotlinStdlibDependency() {
        val pluginOrderBugProject = getBuild("PluginOrderBugTest_testTransitiveKotlinStdlibDependency")
        pluginOrderBugProject.extraProperties.add("-Pkotlin_version=1.9.20")
        val projectKotlinVersion = pluginOrderBugProject.getKotlinVersion()
        assertTrue(projectKotlinVersion == "1.9.20")
        val dependencies = pluginOrderBugProject.dependencies()
        assertFalse(dependencies.output.contains("org.jetbrains.kotlin:kotlin-stdlib:{strictly $kotlinVersion}"),
            "Strict requirement for 'org.jetbrains.kotlin:kotlin-stdlib:{strictly $kotlinVersion}' was found in the project ${pluginOrderBugProject.projectName} dependencies, " +
                    "while Kotlin version used in the project is $projectKotlinVersion"
        )
    }
}
