package kotlinx.atomicfu.plugin.gradle.test

import kotlinx.atomicfu.plugin.gradle.internal.*
import org.junit.*

/**
 * Test that ensures correctness of `atomicfu-gradle-plugin` application to the MPP project,
 * - JVM IR compiler plugin transformation (kotlinx.atomicfu.enableJvmIrTransformation=true)
 * - no post-compilation bytecode transformation tasks are created
 * - post-compilation js file transformation task created (as only JVM IR transformation applied, js is transformed in legacy mode)
 * - no `kotlinx/atomicfu` references are left in the transformed bytecode.
 */
class MppJvmIrTransformationTest : BaseKotlinGradleTest("mpp-simple") {

    override fun BaseKotlinScope.createProject() {
        buildGradleKts {
            resolve("projects/mpp-simple/mpp-simple.gradle.kts")
        }
        settingsGradleKts {
            resolve("projects/mpp-simple/settings.gradle.kts")
        }
        gradleProperties {
            resolve("projects/mpp-simple/gradle.properties_jvm")
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
    fun testPluginApplication() =
        checkTaskOutcomes(
            executedTasks = listOf(
                ":compileKotlinJvm",
                ":compileTestKotlinJvm",
                ":compileKotlinJs",
                // legacy JS transformation
                ":transformJsMainAtomicfu",
                ":transformJsTestAtomicfu"
            ),
            excludedTasks = listOf(
                ":transformJvmMainAtomicfu",
                ":transformJvmTestAtomicfu"
            )
        )

    @Test
    fun testAtomicfuReferences() {
        runner.build()
        checkBytecode("build/classes/kotlin/jvm/main/IntArithmetic.class")
    }
}

/**
 * Test that ensures correctness of `atomicfu-gradle-plugin` application to the MPP project,
 * - JS IR compiler plugin transformation (kotlinx.atomicfu.enableJsIrTransformation=true)
 * - post-compilation bytecode transformation tasks are created (only JS IR transformation is applied, jvm is transformed in legacy mode)
 * - no post-compilation js file transformation tasks are created
 * - no `kotlinx/atomicfu` references are left in the transformed bytecode.
 */
class MppJsIrTransformationTest : BaseKotlinGradleTest("mpp-simple") {

    override fun BaseKotlinScope.createProject() {
        buildGradleKts {
            resolve("projects/mpp-simple/mpp-simple.gradle.kts")
        }
        settingsGradleKts {
            resolve("projects/mpp-simple/settings.gradle.kts")
        }
        gradleProperties {
            resolve("projects/mpp-simple/gradle.properties_js")
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
    fun testPluginApplication() =
        checkTaskOutcomes(
            executedTasks = listOf(
                ":compileKotlinJvm",
                ":compileTestKotlinJvm",
                ":compileKotlinJs",
                // legacy JVM transformation
                ":transformJvmMainAtomicfu",
                ":transformJvmTestAtomicfu"
            ),
            excludedTasks = listOf(
                ":transformJsMainAtomicfu",
                ":transformJsTestAtomicfu"
            )
        )

    @Test
    fun testAtomicfuReferences() {
        runner.build()
        checkBytecode("build/classes/atomicfu/jvm/main/IntArithmetic.class")
    }
}

/**
 * Test that ensures correctness of `atomicfu-gradle-plugin` application to the MPP project,
 * - JS IR and JVM IR compiler plugin transformation
 * - no post-compilation bytecode transformation tasks are created (only JS IR transformation is applied, jvm is transformed in legacy mode)
 * - no post-compilation js file transformation tasks are created
 * - no `kotlinx/atomicfu` references are left in the transformed bytecode.
 */
class MppBothIrTransformationTest : BaseKotlinGradleTest("mpp-simple") {

    override fun BaseKotlinScope.createProject() {
        buildGradleKts {
            resolve("projects/mpp-simple/mpp-simple.gradle.kts")
        }
        settingsGradleKts {
            resolve("projects/mpp-simple/settings.gradle.kts")
        }
        gradleProperties {
            resolve("projects/mpp-simple/gradle.properties_both")
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
    fun testPluginApplication() =
        checkTaskOutcomes(
            executedTasks = listOf(
                ":compileKotlinJvm",
                ":compileTestKotlinJvm",
                ":compileKotlinJs"
            ),
            excludedTasks = listOf(
                ":transformJvmMainAtomicfu",
                ":transformJvmTestAtomicfu",
                ":transformJsMainAtomicfu",
                ":transformJsTestAtomicfu"
            )
        )

    @Test
    fun testAtomicfuReferences() {
        runner.build()
        checkBytecode("build/classes/kotlin/jvm/main/IntArithmetic.class")
    }
}
