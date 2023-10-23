/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package test

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.Test

class MppProjectTest {
    private val mppSample: GradleBuild = createGradleBuildFromSources("mpp-sample")

    @Test
    fun testMppJvm1() {
        mppSample.enableJvmIrTransformation = true
        mppSample.checkMppJvmCompileOnlyDependencies()
        mppSample.checkConsumableDependencies()
        mppSample.buildAndCheckBytecode()
    }

    @Test
    fun testMppJvm2() {
        mppSample.enableJvmIrTransformation = false
        mppSample.checkMppJvmCompileOnlyDependencies()
        mppSample.checkConsumableDependencies()
        mppSample.buildAndCheckBytecode()
    }

    // TODO: JS klib will be checked for kotlinx.atomicfu references when this issue KT-61143 is fixed.
    @Test
    fun testMppJs1() {
        mppSample.enableJsIrTransformation = true
        assert(mppSample.build().isSuccessful)
        mppSample.checkConsumableDependencies()
    }

    @Test
    fun testMppJs2() {
        mppSample.enableJsIrTransformation = false
        assert(mppSample.build().isSuccessful)
        mppSample.checkConsumableDependencies()
    }
}