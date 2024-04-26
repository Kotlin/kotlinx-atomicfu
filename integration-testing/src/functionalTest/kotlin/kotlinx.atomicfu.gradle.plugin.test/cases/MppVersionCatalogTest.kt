package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.*

/**
 * This test checks the build of mpp-version-catalog project that uses a versions catalog and was a reproducer for this error (#399).
 */
class MppVersionCatalogTest {
    private val mppWithVersionCatalog: GradleBuild = createGradleBuildFromSources("mpp-version-catalog")

    @Test
    fun testBuildWithKotlinNewerThan_1_9_0() {
        mppWithVersionCatalog.enableJvmIrTransformation = true
        mppWithVersionCatalog.enableNativeIrTransformation = true
        mppWithVersionCatalog.cleanAndBuild()
        mppWithVersionCatalog.buildAndCheckBytecode()
    }
}
