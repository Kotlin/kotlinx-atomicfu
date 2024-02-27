package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.buildAndCheckBytecode
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.*

/**
 * This test checks kotlinx-atomicfu plugin application to one module within a project containing multiple modules.
 * 
 * `multi-module-test` is also a reproducer for the problem with leftovers of atomicfu references in Metadata, see KT-63413
 */
class MultiModuleTest {
    private val multiModuleTest: GradleBuild = createGradleBuildFromSources("multi-module-test")

    @Test
    fun testMppWithDisabledJvmIrTransformation() {
        multiModuleTest.enableJvmIrTransformation = false
        val buildResult = multiModuleTest.cleanAndBuild()
        assertTrue(buildResult.isSuccessful, buildResult.output)
        multiModuleTest.buildAndCheckBytecode()
    }

    @Test
    fun testMppWithEnabledJvmIrTransformation() {
        multiModuleTest.enableJvmIrTransformation = true
        val buildResult = multiModuleTest.cleanAndBuild()
        assertTrue(buildResult.isSuccessful, buildResult.output)
        multiModuleTest.buildAndCheckBytecode()
    }
}
