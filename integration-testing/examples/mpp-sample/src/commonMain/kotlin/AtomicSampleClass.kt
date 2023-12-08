/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package examples.mpp_sample

import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.*
import kotlin.test.*

public class AtomicSampleClass {
    private val _x = atomic(0)
    val x get() = _x.value

    public fun doWork(finalValue: Int) {
        assertEquals(0, x)
        assertEquals(0, _x.getAndSet(3))
        assertEquals(3, x)
        assertTrue(_x.compareAndSet(3, finalValue))
    }
    
    private val lock = reentrantLock()
    
    public fun synchronizedFoo(value: Int): Int {
        return lock.withLock { value }
    }
}
