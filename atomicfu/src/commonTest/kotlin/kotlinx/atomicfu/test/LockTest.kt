/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

class LockTest {
    private val inProgressLock = atomic(false)

    @Test
    fun testLock() {
        var result = ""
        if (inProgressLock.tryAcquire()) {
            result = "OK"
        }
        assertEquals("OK", result)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun AtomicBoolean.tryAcquire(): Boolean = compareAndSet(false, true)
