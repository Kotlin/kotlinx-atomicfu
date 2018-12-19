/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.plugin.gradle

import org.junit.Test

class EmptyProjectTest : BaseKotlinGradleTest() {
    @Test
    fun testEmpty() = project("empty") {
        build("build") {}
    }
}
