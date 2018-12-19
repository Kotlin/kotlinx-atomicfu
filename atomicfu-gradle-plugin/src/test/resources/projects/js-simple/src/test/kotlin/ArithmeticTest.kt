/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlin.test.*

class ArithmeticTest {
    @Test
    fun testInt() {
        val a = IntArithmetic()
        doWork(a)
        check(a.x == 8)
    }
}