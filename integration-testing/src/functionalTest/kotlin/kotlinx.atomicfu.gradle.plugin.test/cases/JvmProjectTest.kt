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
        jvmSample.jvmCheckAtomicfuInCompileClasspath()
        jvmSample.jvmCheckNoAtomicfuInRuntimeConfigs()
        jvmSample.checkConsumableDependencies()
        jvmSample.buildAndCheckBytecode()
    }

    @Test
    fun testJvmWithDisabledIrTransformation() {
        jvmSample.enableJvmIrTransformation = false
        jvmSample.jvmCheckAtomicfuInCompileClasspath()
        jvmSample.jvmCheckNoAtomicfuInRuntimeConfigs()
        jvmSample.checkConsumableDependencies()
        jvmSample.buildAndCheckBytecode()
    }
    
    // This test checks that jar is packed without duplicates, see #303
    @Test
    fun testJar() {
        assertTrue(jvmSample.cleanAndJar().isSuccessful)
    }
}
