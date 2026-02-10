package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.buildAndCheckBytecode
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import org.junit.Rule
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * This test checks kotlinx-atomicfu plugin application to one module within a project containing multiple modules.
 * 
 * `multi-module-test` is also a reproducer for the problem with leftovers of atomicfu references in Metadata, see KT-63413
 */
class MultiModuleTest {
    private val multiModuleTest: GradleBuild = createGradleBuildFromSources("multi-module-test")

    @get:Rule
    val timeout = Timeout(30L, TimeUnit.MINUTES)

    @Test
    fun testMppWithDisabledJvmIrTransformation() {
        multiModuleTest.enableJvmIrTransformation = false
        multiModuleTest.buildAndCheckBytecode()
    }

    @Test
    fun testMppWithEnabledJvmIrTransformation() {
        multiModuleTest.enableJvmIrTransformation = true
        multiModuleTest.buildAndCheckBytecode()
    }
}
