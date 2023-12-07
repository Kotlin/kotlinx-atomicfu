/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlin.test.*
import kotlinx.atomicfu.examples.mpp_sample.*

class AtomicSampleTest {

    @Test
    fun testInt() {
        val a = AtomicSampleClass()
        a.doWork(1234)
        assertEquals(1234, a.x)
        assertEquals(42, a.synchronizedFoo(42))
    }
}
