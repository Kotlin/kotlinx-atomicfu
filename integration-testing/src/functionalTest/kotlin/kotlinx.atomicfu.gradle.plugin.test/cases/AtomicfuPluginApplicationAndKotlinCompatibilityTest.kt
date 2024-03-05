package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.*

/**
 * This test checks 
 */
// todo get the version of atomicfu on the classpath with a different native version
// todo: check different kotlin versions and expected warnings 
class AtomicfuPluginApplicationAndKotlinCompatibilityTest {
    private val mppWithVersionCatalog: GradleBuild = createGradleBuildFromSources("mpp-version-catalog")

    @Test
    fun testBuildWithKotlinNewerThan_1_9_0() {
        mppWithVersionCatalog.enableJvmIrTransformation = true
        mppWithVersionCatalog.enableNativeIrTransformation = true
        mppWithVersionCatalog.kotlinVersion = "1.9.22"
        val buildResult = mppWithVersionCatalog.cleanAndBuild()
        assertTrue(buildResult.isSuccessful, buildResult.output)
        mppWithVersionCatalog.buildAndCheckBytecode()
    }

    @Test
    fun testBuildWithKotlinOlderThan_1_9_0() {
        mppWithVersionCatalog.enableJvmIrTransformation = true
        mppWithVersionCatalog.enableNativeIrTransformation = true
        // TODO: the Kotlin version is not actualy passed as a parameter for this test build.
        mppWithVersionCatalog.kotlinVersion = "1.8.20"
        val buildResult = mppWithVersionCatalog.cleanAndBuild()
        assertTrue(buildResult.isSuccessful, buildResult.output)
        mppWithVersionCatalog.buildAndCheckBytecode()
    }
    
}
