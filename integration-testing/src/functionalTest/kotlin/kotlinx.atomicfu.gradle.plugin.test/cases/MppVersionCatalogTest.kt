package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import org.junit.Rule
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * This test checks the build of mpp-version-catalog project that uses a versions catalog and was a reproducer for this error (#399).
 */
class MppVersionCatalogTest {
    private val mppWithVersionCatalog: GradleBuild = createGradleBuildFromSources("mpp-version-catalog")

    @get:Rule
    val timeout = Timeout(30L, TimeUnit.MINUTES)

    @Test
    fun testBuildWithKotlinNewerThan_1_9_0() {
        mppWithVersionCatalog.enableJvmIrTransformation = true
        mppWithVersionCatalog.enableNativeIrTransformation = true
        mppWithVersionCatalog.buildAndCheckBytecode()
    }
}
