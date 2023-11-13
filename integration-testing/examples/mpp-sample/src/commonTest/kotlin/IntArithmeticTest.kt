/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlin.test.*

class IntArithmeticTest {

    @Test
    fun testInt() {
        val a = IntArithmetic()
        a.doWork(1234)
        assertEquals(1234, a.x)
    }
}
