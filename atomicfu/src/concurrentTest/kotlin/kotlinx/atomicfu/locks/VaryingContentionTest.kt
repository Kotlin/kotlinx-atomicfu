package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertTrue

class VaryingContentionTest {
    
    @Test
    fun varyingContentionTest() {
        val lockInt = LockInt()
        multiTestLock(lockInt, 10, 100000)
        multiTestLock(lockInt, 1, 200000)
        multiTestLock(lockInt, 20, 300000)
        multiTestLock(lockInt, 1, 400000)
    }

    
    private fun multiTestLock(lockInt: LockInt, nThreads: Int, countTo: Int) {
        val futureList = List(nThreads) { i ->
            testWithThread(lockInt, countTo, nThreads, i)
        }
        Fut.waitAllAndThrow(futureList)
    }

    private fun testWithThread(lockInt: LockInt, max: Int, mod: Int, id: Int): Fut {
        return Fut {
            while (true) {
                lockInt.lock()
                try {
                    if (lockInt.n % mod == id) lockInt.n++
                    if (lockInt.n >= max) break
                } finally {
                    lockInt.unlock()
                }
            }
        }
    }
    
    class LockInt {
        private val lock = SynchronousMutex()
        private val check = atomic(0)
        var n = 0
        fun lock() {
            lock.lock()
            assertTrue(check.incrementAndGet() == 1)
        }
        fun unlock() {
            assertTrue(check.decrementAndGet() == 0)
            lock.unlock()
        }
    }
}