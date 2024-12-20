package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class LockWithTimeoutTests {

    // Helper class with atomic counter in constructor
    class AtomicCounter(initialValue: Int = 0) {
        val counter = atomic(initialValue)

        fun incrementAndGet(): Int = counter.incrementAndGet()
        val value: Int get() = counter.value
    }

    @Test
    fun timeoutLockStressTest() {
        val mutex = SynchronousMutex()
        val counter = AtomicCounter(0)
        val targetCount = 1000
        val threads = mutableListOf<TestThread>()

        // Create 5 test threads
        repeat(5) { threadId ->
            val thread = testThread {
                while (counter.value < targetCount) {
                    // Try to acquire the lock with a timeout
                    if (mutex.tryLock((Random.nextInt(1, 10)).milliseconds)) {
                        try {
                            // Increment the counter if lock was acquired
                            if (counter.value < targetCount) {
                                counter.incrementAndGet()
                            }
                            // Random sleep to increase variation
                            sleepMillis(Random.nextInt(0, 5).toLong())
                        } finally {
                            mutex.unlock()
                        }
                    }

                    // Random sleep between attempts to increase variation
                    sleepMillis(Random.nextInt(0, 3).toLong())
                }
            }
            threads.add(thread)
        }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        // Verify the counter reached the target
        assertEquals(targetCount, counter.value)
    }

}
