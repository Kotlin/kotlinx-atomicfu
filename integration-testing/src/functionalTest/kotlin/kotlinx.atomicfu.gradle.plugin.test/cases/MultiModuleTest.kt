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
    private fun getBuild(testName: String): GradleBuild = createGradleBuildFromSources("multi-module-test", testName)

    @Test
    fun testMppWithDisabledJvmIrTransformation() {
        val multiModuleTest = getBuild("MultiModuleTest_testMppWithDisabledJvmIrTransformation")
        multiModuleTest.enableJvmIrTransformation = false
        multiModuleTest.buildAndCheckBytecode()
    }

    @Test
    fun testMppWithEnabledJvmIrTransformation() {
        val multiModuleTest = getBuild("MultiModuleTest_testMppWithEnabledJvmIrTransformation")
        multiModuleTest.enableJvmIrTransformation = true
        multiModuleTest.buildAndCheckBytecode()
    }
}
