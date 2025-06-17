package kotlinx.atomicfu.locks

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration

private const val TEST_ITERATIONS = 5
private const val MAX_RANDOM_WAIT_MILLIS = 5L
private val THREAD_SETS = listOf(2, 5, 7)

class BarrierTest {
    private class Arrs(numberOfThreads: Int) {
        val after = AtomicIntArray(numberOfThreads)
        val before = AtomicIntArray(numberOfThreads)

        init {
            repeat(numberOfThreads) {
                after[it].value = 0
                before[it].value = 0
            }
        }
    }

    @Test
    fun testBarrier() {
        repeat(TEST_ITERATIONS) { iteration ->
            THREAD_SETS.forEach { numberOfThreads ->
                val barrier = Barrier(numberOfThreads)
                val ar = Arrs(numberOfThreads)
                val threads = List(numberOfThreads) { myThread ->
                    Fut {
                        repeat(numberOfThreads) { otherThread ->
                            if (otherThread != myThread && ar.after[otherThread].value != 0) {
                                fail("Thread $myThread arrived too early")
                            }
                        }
                        sleepMillis(Random.nextLong(MAX_RANDOM_WAIT_MILLIS))
                        ar.before[myThread].value = 1

                        barrier.await()

                        ar.after[myThread].value = 1
                        repeat(numberOfThreads) { otherThread ->
                            if (ar.before[otherThread].value == 0) {
                                fail("Thread $myThread continued too early: $otherThread had value ${ar.before[otherThread].value}")
                            }
                        }
                    }
                }
                Fut.waitAllAndThrow(threads)
            }
        }
    }
}

/**
 * Single-use barrier that blocks all participants until they all arrive.
 */
private class Barrier(private val parties: Int) {
    init {
        require(parties > 1)
    }

    private val count = atomic(0)
    private val waiters = atomicArrayOfNulls<Any?>(parties - 1)

    fun await() {
        val myIndex = count.getAndIncrement()
        if (myIndex == parties - 1) {
            wakeUpEveryone()
            return
        }
        val currentThread = ParkingSupport.currentThreadHandle()
        while (true) {
            val waiter = waiters[myIndex].value
            when {
                waiter === null -> waiters[myIndex].compareAndSet(null, currentThread)
                waiter === FINISHED -> return
                else -> ParkingSupport.park(Duration.INFINITE)
            }
        }
    }

    private fun wakeUpEveryone() {
        for (i in 0..<parties - 1) {
            while (true) {
                val waiter = waiters[i].value
                if (waiters[i].compareAndSet(waiter, FINISHED)) {
                    if (waiter is ParkingHandle) {
                        ParkingSupport.unpark(waiter)
                    } else {
                        check(waiter === null) { "Barrier used more than once: got $waiter" }
                    }
                    break
                }
            }
        }
    }
}

private val FINISHED = Any()
