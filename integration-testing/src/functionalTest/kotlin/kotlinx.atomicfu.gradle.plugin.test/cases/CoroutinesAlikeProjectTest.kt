/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package  kotlinx.atomicfu.gradle.plugin.test.cases

import kotlinx.atomicfu.gradle.plugin.test.framework.runner.*
import kotlin.test.*

class CoroutinesAlikeProjectTest {
    private val mppSample: GradleBuild = createGradleBuildFromSources("coroutines-alike-project")

    @Test
    fun testNativeMetadataCompilation() {
        mppSample.runGradle(listOf("clean", "compileNativeMainKotlinMetadata")).also {
            require(it.isSuccessful) {
                it.output
            }
        }
    }
}
