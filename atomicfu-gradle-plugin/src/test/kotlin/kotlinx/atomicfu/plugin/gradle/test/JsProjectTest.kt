package kotlinx.atomicfu.plugin.gradle.test

import kotlinx.atomicfu.plugin.gradle.internal.*
import kotlinx.atomicfu.plugin.gradle.internal.BaseKotlinScope
import org.junit.Test

/**
 * Test that ensures correctness of `atomicfu-gradle-plugin` application to the JS project:
 * - post-compilation js transformation tasks are created
 *   (legacy transformation is tested here, compiler plugin is not applied).
 * - original non-transformed classes are not left in compile/runtime classpath.
 */
class JsLegacyTransformationTest : BaseKotlinGradleTest("js-simple") {

    override fun BaseKotlinScope.createProject() {
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
    fun testPluginApplication() =
        checkTaskOutcomes(
            executedTasks = listOf(
                ":compileKotlinJs",
                ":transformJsMainAtomicfu",
                ":compileTestKotlinJs",
                ":transformJsTestAtomicfu"
            ),
            excludedTasks = emptyList()
        )

    @Test
    fun testClasspath() {
        runner.build()
        checkJsCompilationClasspath()
    }
}
