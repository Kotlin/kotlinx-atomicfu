/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package bytecode_test

import kotlinx.atomicfu.locks.*
import kotlin.test.*

class ReentrantLockTest {
    private val lock_constructor = ReentrantLock()
    private val lock_factory = reentrantLock()
    private var state = 0

    @Test
    fun testLockField() {
        lock_constructor.withLock { 
            state = 6
        }
        assertEquals(6, state)
        lock_factory.withLock {
            state = 1
        }
        assertEquals(1, state)
    }
}
