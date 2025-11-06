package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.GradleBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.LOCAL_REPO_DIR_PREFIX
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.cleanAndBuild
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.createGradleBuildFromSources
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.publishToLocalRepository
import kotlin.test.*

/**
 * This test builds a project that depends on the library that uses atomicfu,
 * without having a direct atomicfu dependency.
 * 
 * The test emulates a project that uses kotlinx.coroutines built with the current atomicfu plugin,
 * and does not provide a direct atomicfu dependency.
 */
class UserProjectTest {
    // Build mpp-sample project as the mpp library and publish it to the local repo
    private val mppSample: GradleBuild = createGradleBuildFromSources("mpp-sample", "UserProjectTest_testUserProjectBuild")
    
    private val userProject: GradleBuild = createGradleBuildFromSources("user-project", "UserProjectTest_testUserProjectBuild")

    @Test
    fun testUserProjectBuild() {
        mppSample.enableNativeIrTransformation = true
        mppSample.publishToLocalRepository()
        val mppSamplePublishDirectory = mppSample.targetDir.resolve(LOCAL_REPO_DIR_PREFIX)
        require(mppSamplePublishDirectory.exists() && mppSamplePublishDirectory.isDirectory) {
            "Failed to find the local repository for ${mppSample.projectName}, this directory does not exist: ${mppSamplePublishDirectory.path}"
        }
        userProject.localRepositoryUrl = mppSamplePublishDirectory.path
        userProject.cleanAndBuild()
    }
}
