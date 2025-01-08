/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package test

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.*
import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.*

class MppProjectTest {
    private val mppSample: GradleBuild = createGradleBuildFromSources("mpp-sample")

//    @Test
//    fun testMppWithEnabledJvmIrTransformation() {
//        mppSample.enableJvmIrTransformation = true
//        mppSample.mppCheckAtomicfuInCompileClasspath("jvm")
//        mppSample.mppCheckNoAtomicfuInRuntimeConfigs("jvm")
//        mppSample.checkConsumableDependencies()
//        mppSample.buildAndCheckBytecode()
//    }
//
//    @Test
//    fun testMppWithDisabledJvmIrTransformation() {
//        mppSample.enableJvmIrTransformation = false
//        mppSample.mppCheckAtomicfuInCompileClasspath("jvm")
//        mppSample.mppCheckNoAtomicfuInRuntimeConfigs("jvm")
//        mppSample.checkConsumableDependencies()
//        mppSample.buildAndCheckBytecode()
//    }
//
//    // TODO: JS klib will be checked for kotlinx.atomicfu references when this issue KT-61143 is fixed.
//    @Test
//    fun testMppWithEnabledJsIrTransformation() {
//        mppSample.enableJsIrTransformation = true
//        mppSample.cleanAndBuild()
//        mppSample.checkConsumableDependencies()
//        mppSample.mppCheckAtomicfuInApi("js")
//    }
//
////    @Test
////    fun testMppWithDisabledJsIrTransformation() {
////        mppSample.enableJsIrTransformation = false
////        mppSample.cleanAndBuild()
////        mppSample.checkConsumableDependencies()
////        mppSample.mppCheckAtomicfuInApi("js")
////    }
//
//    @Test
//    fun testMppWasmJsBuild() {
//        mppSample.cleanAndBuild()
//        mppSample.mppCheckAtomicfuInCompileClasspath("wasmJs")
//        mppSample.mppCheckAtomicfuInRuntimeClasspath("wasmJs")
//        mppSample.checkConsumableDependencies()
//        mppSample.mppCheckAtomicfuInApi("wasmJs")
//    }
//
//    @Test
//    fun testMppWasmWasiBuild() {
//        mppSample.mppCheckAtomicfuInCompileClasspath("wasmWasi")
//        mppSample.mppCheckAtomicfuInRuntimeClasspath("wasmWasi")
//        mppSample.checkConsumableDependencies()
//        mppSample.mppCheckAtomicfuInApi("wasmWasi")
//    }
//
////    @Test
////    fun testMppNativeWithEnabledIrTransformation() {
////        mppSample.enableNativeIrTransformation = true
////        mppSample.cleanAndBuild()
////        // When Native IR transformations are applied, atomicfu-gradle-plugin still provides transitive atomicfu dependency
////        mppSample.mppNativeCheckAtomicfuInImplementation("macosX64")
////        mppSample.checkConsumableDependencies()
////        mppSample.mppCheckAtomicfuInApi("macosX64")
////        // TODO: klib checks are skipped for now because of this problem KT-61143
////        //mppSample.buildAndCheckNativeKlib()
////    }
//
//    @Test
//    fun testMppNativeWithDisabledIrTransformation() {
//        mppSample.enableNativeIrTransformation = false
//        mppSample.cleanAndBuild()
//        mppSample.mppNativeCheckAtomicfuInImplementation("macosX64")
//        mppSample.checkConsumableDependencies()
//        mppSample.mppCheckAtomicfuInApi("macosX64")
//        // TODO: klib checks are skipped for now because of this problem KT-61143
//        //mppSample.buildAndCheckNativeKlib()
//    }
}
