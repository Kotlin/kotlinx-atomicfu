package kotlinx.atomicfu.locks

import kotlinx.atomicfu.*
import kotlin.native.concurrent.*
import kotlin.test.*

class NonReentrantLockTest {

    private val lock = NonReentrantLock()
    private val condition = lock.Condition()
    private var counter = 0
    private val iterations = 1_000
    private val workers = 8
    private val expectedResult = iterations * workers
    private var isDone = atomic(false)

    @Test
    fun testStress() {
        val workers = List(workers) { Worker.start() }
        workers.forEach {
            it.executeAfter {
                repeat(iterations) {
                    lock.lock()
                    try {
                        if (++counter == expectedResult) {
                            isDone.value = true
                            condition.notifyOne()
                        }
                    } finally {
                        lock.unlock()
                    }
                }
            }
        }


        lock.lock()
        try {
            while (!isDone.value) {
                condition.wait()
            }
        } finally {
            lock.unlock()
        }
        assertEquals(expectedResult, counter)
        lock.destroy()
        condition.destroy()
    }
}
