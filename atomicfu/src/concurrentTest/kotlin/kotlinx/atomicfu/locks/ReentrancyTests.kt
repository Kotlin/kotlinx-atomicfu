package kotlinx.atomicfu.locks

import kotlin.test.Test
import kotlin.test.assertFails

class ReentrancyTests {
    
    @Test
    fun reentrantTestSuccess() {
        val lock = NativeMutex()
        lock.lock()
        lock.lock()
        lock.unlock()
        lock.unlock()
    }
    
    @Test
    fun reentrantTestFail() {
        val lock = NativeMutex()
        lock.lock()
        lock.lock()
        lock.unlock()
        lock.unlock()
        assertFails {
            lock.unlock()
        }
    }
}