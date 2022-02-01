package kotlinx.atomicfu.plugin.gradle.test

import kotlinx.atomicfu.plugin.gradle.internal.*
import org.junit.*

class MppProjectTest : BaseKotlinGradleTest("mpp-simple") {
    private fun BaseKotlinScope.createProject() {
        buildGradleKts {
            resolve("projects/mpp-simple/mpp-simple.gradle.kts")
        }
        settingsGradleKts {
            resolve("projects/mpp-simple/settings.gradle.kts")
        }
        dir("src/commonMain/kotlin") {}
        kotlin("IntArithmetic.kt", "commonMain") {
            resolve("projects/mpp-simple/src/commonMain/kotlin/IntArithmetic.kt")
        }
        dir("src/commonTest/kotlin") {}
        kotlin("ArithmeticTest.kt", "commonTest") {
            resolve("projects/mpp-simple/src/commonTest/kotlin/ArithmeticTest.kt")
        }
    }

    @Test
    fun testPlugin() {
        val runner = test {
            createProject()
            runner {
                arguments.add(":build")
            }
        }
        val tasksToCheck = arrayOf(
            ":compileKotlinJvm",
            ":compileTestKotlinJvm",
            ":transformJvmMainAtomicfu",
            ":transformJvmTestAtomicfu",
            ":compileKotlinJs",
            ":transformJsMainAtomicfu"
        )
        runner.build().apply {
            tasksToCheck.forEach {
                assertTaskSuccess(it)
            }
        }
        // check that task outcomes are cached for the second build
        runner.build().apply {
            tasksToCheck.forEach {
                assertTaskUpToDate(it)
            }
        }

        checkJvmCompilationClasspath(
            originalClassFile = "build/classes/kotlin/jvm/main/IntArithmetic.class",
            transformedClassFile = "build/classes/atomicfu/jvm/main/IntArithmetic.class"
        )
        checkJsCompilationClasspath()
    }
}