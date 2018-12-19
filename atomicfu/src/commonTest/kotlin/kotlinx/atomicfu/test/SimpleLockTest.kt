/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

class SimpleLockTest {
    @Test
    fun withLock() {
        val lock = SimpleLock()
        val result = lock.withLock {
            "OK"
        }
        assertEquals("OK", result)
    }
}

class SimpleLock {
    private val _locked = atomic(0)

    fun <T> withLock(block: () -> T): T {
        // this contrieves construct triggers Kotlin compiler to reuse local variable slot #2 for
        // the exception in `finally` clause
        try {
            _locked.loop { locked ->
                check(locked == 0)
                if (!_locked.compareAndSet(0, 1)) return@loop // continue
                return block()
            }
        } finally {
            _locked.value = 0
        }
    }
}