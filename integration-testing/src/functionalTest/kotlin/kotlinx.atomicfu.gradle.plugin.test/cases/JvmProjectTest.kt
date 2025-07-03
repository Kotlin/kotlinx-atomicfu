/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.*

class JvmProjectTest {

    private val jvmSample: GradleBuild = createGradleBuildFromSources("jvm-sample")

    @Test
    fun testJvmWithEnabledIrTransformation() {
        jvmSample.enableJvmIrTransformation = true
        jvmSample.withDependencies {
            jvmCheckAtomicfuInCompileClasspath()
            jvmCheckNoAtomicfuInRuntimeConfigs()
        }
        jvmSample.checkConsumableDependencies(false)
        jvmSample.buildAndCheckBytecode()
    }

    @Test
    fun testJvmWithDisabledIrTransformation() {
        jvmSample.enableJvmIrTransformation = false
        jvmSample.withDependencies {
            jvmCheckAtomicfuInCompileClasspath()
            jvmCheckNoAtomicfuInRuntimeConfigs()
        }
        jvmSample.checkConsumableDependencies(false)
        jvmSample.buildAndCheckBytecode()
    }

    // This test checks that jar is packed without duplicates, see #303
    @Test
    fun testJar() {
        assertTrue(jvmSample.cleanAndJar().isSuccessful)
    }

    @Test
    fun testFilesDeleted() {

        val buildClassesAtomicfuDir = jvmSample.targetDir.resolve("build/classes/atomicfu")

        /** Get all that Atomicfu generates in `build/classes/atomicfu/` */
        fun buildClassesAtomicFuDirFiles(): String =
            buildClassesAtomicfuDir.walk()
                .filter { it.isFile }
                .map { it.relativeTo(jvmSample.targetDir).invariantSeparatorsPath }
                .sorted()
                .joinToString("\n")

        jvmSample.build().apply {
            assertTrue { buildClassesAtomicfuDir.exists() }
            assertEquals(
                """
                build/classes/atomicfu/main/IntArithmetic.class
                build/classes/atomicfu/main/META-INF/jvm-sample.kotlin_module
                build/classes/atomicfu/main/MainKt.class
                build/classes/atomicfu/test/ArithmeticTest.class
                build/classes/atomicfu/test/META-INF/jvm-sample_test.kotlin_module
                """.trimIndent(),
                buildClassesAtomicFuDirFiles()
            )
        }

        val projectSrcDir = jvmSample.targetDir.resolve("src")
        projectSrcDir.deleteRecursively()
        assertFalse { projectSrcDir.exists() }

        jvmSample.build().apply {
            assertTrue { buildClassesAtomicfuDir.exists() }
            assertEquals(
                "",
                buildClassesAtomicFuDirFiles(),
            )
        }
    }
}
