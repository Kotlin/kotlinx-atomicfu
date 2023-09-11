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
        mppSample.checkCosumableDependencies()
        mppSample.buildAndCheckBytecode()
    }

    @Test
    fun testMppJvm2() {
        mppSample.enableJvmIrTransformation = false
        mppSample.checkMppJvmCompileOnlyDependencies()
        mppSample.checkCosumableDependencies()
        mppSample.buildAndCheckBytecode()
    }

    // TODO: JS klib should be checked for atomicfu references
    @Test
    fun testMppJs1() {
        mppSample.enableJsIrTransformation = true
        assert(mppSample.build().isSuccessful)
    }

    @Test
    fun testMppJs2() {
        mppSample.enableJsIrTransformation = false
        assert(mppSample.build().isSuccessful)
    }
}