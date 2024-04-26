/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package test

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.*

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
        mppSample.cleanAndBuild()
        mppSample.checkConsumableDependencies()
    }

    @Test
    fun testMppWithDisabledJsIrTransformation() {
        mppSample.enableJsIrTransformation = false
        mppSample.cleanAndBuild()
        mppSample.checkConsumableDependencies()
    }

    @Test
    fun testMppWasmBuild() {
        mppSample.cleanAndBuild()
        mppSample.checkMppWasmJsImplementationDependencies()
        mppSample.checkMppWasmWasiImplementationDependencies()
    }

    @Test
    fun testMppNativeWithEnabledIrTransformation() {
        mppSample.enableNativeIrTransformation = true
        mppSample.cleanAndBuild()
        // When Native IR transformations are applied, atomicfu-gradle-plugin still provides transitive atomicfu dependency
        mppSample.checkMppNativeImplementationDependencies()
        // TODO: klib checks are skipped for now because of this problem KT-61143
        //mppSample.buildAndCheckNativeKlib()
    }

    @Test
    fun testMppNativeWithDisabledIrTransformation() {
        mppSample.enableNativeIrTransformation = false
        mppSample.cleanAndBuild()
        mppSample.checkMppNativeImplementationDependencies()
        // TODO: klib checks are skipped for now because of this problem KT-61143
        //mppSample.buildAndCheckNativeKlib()
    }
}
