/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.atomicfu.*
import kotlin.test.*

class IntArithmetic {
    private val _x = atomic(0)
    val x get() = _x.value

    fun doWork(finalValue: Int) {
        assertEquals(0, x)
        assertEquals(0, _x.getAndSet(3))
        assertEquals(3, x)
        assertTrue(_x.compareAndSet(3, finalValue))
    }
}
