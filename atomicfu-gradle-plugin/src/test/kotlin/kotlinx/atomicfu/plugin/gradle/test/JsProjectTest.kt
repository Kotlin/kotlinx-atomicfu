package kotlinx.atomicfu.plugin.gradle.test

import kotlinx.atomicfu.plugin.gradle.internal.*
import kotlinx.atomicfu.plugin.gradle.internal.BaseKotlinScope
import org.junit.Test

class JsProjectTest : BaseKotlinGradleTest("js-simple") {
    private fun BaseKotlinScope.createProject() {
        buildGradleKts {
            resolve("projects/js-simple/js-simple.gradle.kts")
        }
        settingsGradleKts {
            resolve("projects/js-simple/settings.gradle.kts")
        }
        dir("src/main/kotlin") {}
        kotlin("IntArithmetic.kt", "main") {
            resolve("projects/js-simple/src/main/kotlin/IntArithmetic.kt")
        }
        dir("src/test/kotlin") {}
        kotlin("ArithmeticTest.kt", "test") {
            resolve("projects/js-simple/src/test/kotlin/ArithmeticTest.kt")
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
            ":compileKotlinJs",
            ":transformJsMainAtomicfu",
            ":compileTestKotlinJs",
            ":transformJsTestAtomicfu"
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
        checkJsCompilationClasspath()
    }
}