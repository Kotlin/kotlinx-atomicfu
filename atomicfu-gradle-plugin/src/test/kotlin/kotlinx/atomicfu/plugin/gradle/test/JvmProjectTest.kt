package kotlinx.atomicfu.plugin.gradle.test

import kotlinx.atomicfu.plugin.gradle.internal.*
import kotlinx.atomicfu.plugin.gradle.internal.BaseKotlinScope
import org.junit.Test

class JvmProjectTest : BaseKotlinGradleTest("jvm-simple") {

    private fun BaseKotlinScope.createProject() {
        buildGradleKts {
            resolve("projects/jvm-simple/jvm-simple.gradle.kts")
        }
        settingsGradleKts {
            resolve("projects/jvm-simple/settings.gradle.kts")
        }
        dir("src/main/kotlin") {}
        kotlin("IntArithmetic.kt", "main") {
            resolve("projects/jvm-simple/src/main/kotlin/IntArithmetic.kt")
        }
        dir("src/test/kotlin") {}
        kotlin("ArithmeticTest.kt", "test") {
            resolve("projects/jvm-simple/src/test/kotlin/ArithmeticTest.kt")
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
            ":compileKotlin",
            ":transformAtomicfuClasses",
            ":compileTestKotlin",
            ":transformTestAtomicfuClasses"
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
            originalClassFile = "build/classes/kotlin/main/IntArithmetic.class",
            transformedClassFile = "build/classes/atomicfu/main/IntArithmetic.class"
        )
    }
}