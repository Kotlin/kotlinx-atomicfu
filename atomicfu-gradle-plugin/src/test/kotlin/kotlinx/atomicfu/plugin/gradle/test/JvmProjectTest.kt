package kotlinx.atomicfu.plugin.gradle.test

import kotlinx.atomicfu.plugin.gradle.internal.*
import kotlinx.atomicfu.plugin.gradle.internal.BaseKotlinScope
import org.junit.Test

/**
 * Test that ensures correctness of `atomicfu-gradle-plugin` application to the JVM project:
 * - post-compilation bytecode transformation tasks are created
 *   (legacy transformation is tested here, compiler plugin is not applied).
 * - original non-transformed classes are not left in compile/runtime classpath.
 * - no `kotlinx/atomicfu` references are left in the transformed bytecode.
 */
class JvmLegacyTransformationTest : BaseKotlinGradleTest("jvm-simple") {

    override fun BaseKotlinScope.createProject() {
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
    fun testPluginApplication() =
        checkTaskOutcomes(
            executedTasks = listOf(
                ":compileKotlin",
                ":transformMainAtomicfu",
                ":compileTestKotlin",
                ":transformTestAtomicfu"
            ),
            excludedTasks = emptyList()
        )

    @Test
    fun testClasspath() {
        runner.build()
        checkJvmCompilationClasspath(
            originalClassFile = "build/classes/atomicfu-orig/main/IntArithmetic.class",
            transformedClassFile = "build/classes/atomicfu/main/IntArithmetic.class"
        )
    }

    @Test
    fun testAtomicfuReferences() {
        runner.build()
        checkBytecode("build/classes/atomicfu/main/IntArithmetic.class")
    }
}

/**
 * Test that ensures correctness of `atomicfu-gradle-plugin` application to the JVM project,
 * - JVM IR compiler plugin transformation (kotlinx.atomicfu.enableJvmIrTransformation=true)
 * - no post-compilation bytecode transforming tasks created
 * - no `kotlinx/atomicfu` references are left in the resulting bytecode after IR transformation.
 */
class JvmIrTransformationTest : BaseKotlinGradleTest("jvm-simple") {

    override fun BaseKotlinScope.createProject() {
        buildGradleKts {
            resolve("projects/jvm-simple/jvm-simple.gradle.kts")
        }
        settingsGradleKts {
            resolve("projects/jvm-simple/settings.gradle.kts")
        }
        // set kotlinx.atomicfu.enableJvmIrTransformation=true to apply compiler plugin
        gradleProperties {
            resolve("projects/jvm-simple/gradle.properties")
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
    fun testPluginApplication() =
        checkTaskOutcomes(
            executedTasks = listOf(
                ":compileKotlin",
                ":compileTestKotlin"
            ),
            excludedTasks = listOf(
                ":transformAtomicfu",
                ":transformTestAtomicfu"
            )
        )

    @Test
    fun testAtomicfuReferences() {
        runner.build()
        checkBytecode("build/classes/kotlin/main/IntArithmetic.class")
    }
}
