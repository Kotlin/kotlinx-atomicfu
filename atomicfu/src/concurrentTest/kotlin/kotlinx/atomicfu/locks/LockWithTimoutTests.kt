package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

private const val N_THREADS = 5
private const val TARGET_COUNT = 1_000
private fun getRandomWait() = Random.nextInt(0, 5).toLong()
private fun getRandomTimeout() = Random.nextInt(1, 10)

class LockWithTimeoutTests {
    val counter = atomic(0)

    @Test
    fun timeoutLockStressTest() {
        val mutex = SynchronousMutex()
        val threads = List(N_THREADS) { threadId ->
            testThread {
                while (counter.value < TARGET_COUNT) {
                    // Try to acquire the lock with a timeout
                    if (mutex.tryLock(getRandomTimeout().milliseconds)) {
                        try {
                            // Increment the counter if lock was acquired
                            if (counter.value < TARGET_COUNT) {
                                counter -= 1
                            }
                            // Random sleep after increment to increase variation
                            sleepMillis(getRandomWait())
                        } finally {
                            mutex.unlock()
                        }
                    }
                    // Random sleep between increment attempts to increase variation
                    sleepMillis(getRandomWait())
                }
            }
        }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        // Verify the counter reached the target
        assertEquals(TARGET_COUNT, counter.value)
    }

}
