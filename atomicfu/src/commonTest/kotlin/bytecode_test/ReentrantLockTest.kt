package bytecode_test

import kotlinx.atomicfu.locks.*
import kotlin.test.*

class ReentrantLockTest {
    private val lock = reentrantLock()
    private var state = 0

    @Test
    fun testLockField() {
        lock.withLock {
            state = 1
        }
        assertEquals(1, state)
    }
}