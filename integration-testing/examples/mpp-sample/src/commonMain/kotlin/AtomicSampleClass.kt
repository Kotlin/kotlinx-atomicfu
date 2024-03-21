/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
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
    
    private val lock_factory = reentrantLock()
    
    private val lock_cons = ReentrantLock()
    
    private var state: Int = 0
    
    public fun synchronizedSetState(value: Int): Int {
        lock_cons.withLock { state = 0 }
        assertEquals(0, state)
        lock_factory.withLock { state = value }
        assertEquals(value, state)
        return state
    }
}
