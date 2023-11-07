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
    fun testMppWithEnabledJvmIrTransformation() {
        mppSample.enableJvmIrTransformation = true
        mppSample.checkMppJvmCompileOnlyDependencies()
        mppSample.checkConsumableDependencies()
        mppSample.buildAndCheckBytecode()
    }

    @Test
    fun testMppWithDisabledJvmIrTransformation() {
        mppSample.enableJvmIrTransformation = false
        mppSample.checkMppJvmCompileOnlyDependencies()
        mppSample.checkConsumableDependencies()
        mppSample.buildAndCheckBytecode()
    }

    // TODO: JS klib will be checked for kotlinx.atomicfu references when this issue KT-61143 is fixed.
    @Test
    fun testMppWithEnabledJsIrTransformation() {
        mppSample.enableJsIrTransformation = true
        assert(mppSample.cleanAndBuild().isSuccessful)
        mppSample.checkConsumableDependencies()
    }

    @Test
    fun testMppWithDisabledJsIrTransformation() {
        mppSample.enableJsIrTransformation = false
        assert(mppSample.cleanAndBuild().isSuccessful)
        mppSample.checkConsumableDependencies()
    }
    
    @Test
    fun testMppWasmBuild() {
        assert(mppSample.cleanAndBuild().isSuccessful)
        mppSample.dependencies()
        mppSample.checkMppWasmJsImplementationDependencies()
    }
}
