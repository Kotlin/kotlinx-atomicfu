package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.runner.GradleBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.cleanAndBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.createGradleBuildFromSources
import kotlin.test.Test

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
