/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package test

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.*

abstract class MppProjectTest {
    internal val mppSample: GradleBuild = createGradleBuildFromSources("mpp-sample")
}

class JvmMppProjectTest : MppProjectTest() {
    @Test
    fun testMppWithEnabledJvmIrTransformation() {
        mppSample.enableJvmIrTransformation = true
        mppSample.withDependencies {
            mppCheckAtomicfuInCompileClasspath("jvm")
            mppCheckNoAtomicfuInRuntimeConfigs("jvm")
        }
        mppSample.checkConsumableDependencies(false)
        mppSample.buildAndCheckBytecode()
    }

    @Test
    fun testMppWithDisabledJvmIrTransformation() {
        mppSample.enableJvmIrTransformation = false
        mppSample.withDependencies {
            mppCheckAtomicfuInCompileClasspath("jvm")
            mppCheckNoAtomicfuInRuntimeConfigs("jvm")
        }
        mppSample.checkConsumableDependencies(false)
        mppSample.buildAndCheckBytecode()
    }
}

class JsMppProjectTest : MppProjectTest() {
    // TODO: JS klib will be checked for kotlinx.atomicfu references when this issue KT-61143 is fixed.
    @Test
    fun testMppWithEnabledJsIrTransformation() {
        mppSample.enableJsIrTransformation = true
        mppSample.checkConsumableDependencies(true)
        mppSample.withDependencies { mppCheckAtomicfuInApi("js") }
    }

    @Test
    fun testMppWithDisabledJsIrTransformation() {
        mppSample.enableJsIrTransformation = false
        mppSample.checkConsumableDependencies(true)
        mppSample.withDependencies { mppCheckAtomicfuInApi("js") }
    }
}

class WasmMppProjectTest : MppProjectTest() {
    @Test
    fun testMppWasmJsBuild() {
        mppSample.checkConsumableDependencies(true)
        mppSample.withDependencies {
            mppCheckAtomicfuInCompileClasspath("wasmJs")
            mppCheckAtomicfuInRuntimeClasspath("wasmJs")
            mppCheckAtomicfuInApi("wasmJs")
        }
    }

    @Test
    fun testMppWasmWasiBuild() {
        mppSample.checkConsumableDependencies(false)
        mppSample.withDependencies {
            mppCheckAtomicfuInCompileClasspath("wasmWasi")
            mppCheckAtomicfuInRuntimeClasspath("wasmWasi")
            mppCheckAtomicfuInApi("wasmWasi")
        }
    }
}

class NativeMppProjectTest : MppProjectTest() {
    @Test
    fun testMppNativeWithEnabledIrTransformation() {
        mppSample.enableNativeIrTransformation = true
        // When Native IR transformations are applied, atomicfu-gradle-plugin still provides transitive atomicfu dependency
        mppSample.checkConsumableDependencies(true)
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
        mppSample.checkConsumableDependencies(true)
        mppSample.withDependencies {
            mppNativeCheckAtomicfuInImplementation("macosX64")
            mppCheckAtomicfuInApi("macosX64")
        }
        // TODO: klib checks are skipped for now because of this problem KT-61143
        //mppSample.buildAndCheckNativeKlib()
    }
}
