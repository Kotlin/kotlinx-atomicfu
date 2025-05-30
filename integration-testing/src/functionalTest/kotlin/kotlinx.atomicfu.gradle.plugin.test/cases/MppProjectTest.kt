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
        mppSample.withDependencies {
            mppCheckAtomicfuInCompileClasspath("jvm")
            mppCheckNoAtomicfuInRuntimeConfigs("jvm")
        }
        mppSample.checkConsumableDependencies()
        mppSample.buildAndCheckBytecode()
    }

    @Test
    fun testMppWithDisabledJvmIrTransformation() {
        mppSample.enableJvmIrTransformation = false
        mppSample.withDependencies {
            mppCheckAtomicfuInCompileClasspath("jvm")
            mppCheckNoAtomicfuInRuntimeConfigs("jvm")
        }
        mppSample.checkConsumableDependencies()
        mppSample.buildAndCheckBytecode()
    }

    // TODO: JS klib will be checked for kotlinx.atomicfu references when this issue KT-61143 is fixed.
    @Test
    fun testMppWithEnabledJsIrTransformation() {
        mppSample.enableJsIrTransformation = true
        mppSample.cleanAndBuild()
        mppSample.checkConsumableDependencies()
        mppSample.withDependencies { mppCheckAtomicfuInApi("js") }
    }

    @Test
    fun testMppWithDisabledJsIrTransformation() {
        mppSample.enableJsIrTransformation = false
        mppSample.cleanAndBuild()
        mppSample.checkConsumableDependencies()
        mppSample.withDependencies { mppCheckAtomicfuInApi("js") }
    }

    @Test
    fun testMppWasmJsBuild() {
        mppSample.cleanAndBuild()
        mppSample.checkConsumableDependencies()
        mppSample.withDependencies {
            mppCheckAtomicfuInCompileClasspath("wasmJs")
            mppCheckAtomicfuInRuntimeClasspath("wasmJs")
            mppCheckAtomicfuInApi("wasmJs")
        }
    }

    @Test
    fun testMppWasmWasiBuild() {
        mppSample.checkConsumableDependencies()
        mppSample.withDependencies {
            mppCheckAtomicfuInCompileClasspath("wasmWasi")
            mppCheckAtomicfuInRuntimeClasspath("wasmWasi")
            mppCheckAtomicfuInApi("wasmWasi")
        }
    }

    @Test
    fun testMppNativeWithEnabledIrTransformation() {
        mppSample.enableNativeIrTransformation = true
        mppSample.cleanAndBuild()
        // When Native IR transformations are applied, atomicfu-gradle-plugin still provides transitive atomicfu dependency
        mppSample.checkConsumableDependencies()
        mppSample.withDependencies {
            mppNativeCheckAtomicfuInImplementation("macosX64")
            mppCheckAtomicfuInApi("macosX64")
        }
        // TODO: klib checks are skipped for now because of this problem KT-61143
        //mppSample.buildAndCheckNativeKlib()
    }

    @Test
    fun testMppNativeWithDisabledIrTransformation() {
        mppSample.enableNativeIrTransformation = false
        mppSample.cleanAndBuild()
        mppSample.checkConsumableDependencies()
        mppSample.withDependencies {
            mppNativeCheckAtomicfuInImplementation("macosX64")
            mppCheckAtomicfuInApi("macosX64")
        }
        // TODO: klib checks are skipped for now because of this problem KT-61143
        //mppSample.buildAndCheckNativeKlib()
    }
}
