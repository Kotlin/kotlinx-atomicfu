package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.runner.GradleBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.cleanAndBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.createGradleBuildFromSources
import kotlin.test.Ignore
import kotlin.test.Test

// This tests may use latest / oldest JDK versions supported by the Kotlin compiler / KGP.
// These versions may not be available on CI agents executing tests and auto-provisioning
// not available on agents running tests from the kotlin-community/dev branch.
// As a result, tests are unstable and have to be disabled.
@Ignore("KTI-1966")
class JvmVersionCompatibilityTest {
    private val jvmSample: GradleBuild = createGradleBuildFromSources("jdk-compatibility")

    @Test
    fun testClassTransformationWithEarliestJdkVersion() {
        jvmSample.enableJvmIrTransformation = false
        jvmSample.extraProperties.add("-PuseMaxVersion=false")
        jvmSample.cleanAndBuild()
    }

    @Test
    fun testClassTransformationWithLatestJdkVersion() {
        jvmSample.enableJvmIrTransformation = false
        jvmSample.extraProperties.add("-PuseMaxVersion=true")
        jvmSample.cleanAndBuild()
    }

    @Test
    fun testIrTransformationWithEarliestJdkVersion() {
        jvmSample.enableJvmIrTransformation = true
        jvmSample.extraProperties.add("-PuseMaxVersion=false")
        jvmSample.cleanAndBuild()
    }

    @Test
    fun testIrTransformationWithLatestJdkVersion() {
        jvmSample.enableJvmIrTransformation = true
        jvmSample.extraProperties.add("-PuseMaxVersion=true")
        jvmSample.cleanAndBuild()
    }
}
