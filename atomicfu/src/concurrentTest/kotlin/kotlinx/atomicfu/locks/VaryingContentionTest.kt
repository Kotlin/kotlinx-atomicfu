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
        val futureList = mutableListOf<Fut>()
        repeat(nThreads) { i ->
            val test = LockIntTest(lockInt, countTo, nThreads, i)
            futureList.add(testWithThread(test))
        }
        Fut.waitAllAndThrow(futureList)
    }

    private fun testWithThread(t: LockIntTest): Fut {
        return Fut {
            while (true) {
                t.lockInt.lock()
                if (t.lockInt.n % t.mod == t.id) t.lockInt.n++
                if (t.lockInt.n >= t.max) {
                    t.lockInt.unlock()
                    break
                }
                t.lockInt.unlock()
            }
        }
    }
    
    data class LockIntTest(
        val lockInt: LockInt,
        val max: Int,
        val mod: Int,
        val id: Int,
    )
    
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