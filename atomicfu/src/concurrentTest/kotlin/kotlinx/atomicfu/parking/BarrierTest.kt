import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.parking.Fut
import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import kotlinx.atomicfu.parking.sleepMills
import kotlin.random.Random
import kotlin.test.*

class BarrierTest {
    private class Arrs(numberOfThreads: Int) {
        val after = atomicArrayOfNulls<Int>(numberOfThreads)
        val before = atomicArrayOfNulls<Int>(numberOfThreads)
        init {repeat(numberOfThreads) {
                after[it].value = 0
                before[it].value = 0
            }}
    }
    @Test
    fun testBarrier() {
        repeat(5) { iteration ->
            println("Barrier test iteration $iteration")
            repeat(5) {
                val numberOfThreads = it + 2
                val barrier = Barrier(numberOfThreads)
                val ar = Arrs(numberOfThreads)
                val threads = List(numberOfThreads) { myThread ->
                    Fut {
                        repeat(numberOfThreads) { otherThread ->
                            if (otherThread != myThread && ar.after[otherThread].value != 0) {
                                fail("Thread $myThread arrived too early")
                            }
                        }
                        sleepMills(Random.nextLong(100))
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
        val currentThread = KThread.currentThread()
        while (true) {
            val waiter = waiters[myIndex].value
            when {
                waiter === null -> waiters[myIndex].compareAndSet(null, currentThread)
                waiter === FINISHED -> return
                else -> Parker.park()
            }
        }
    }

    private fun wakeUpEveryone() {
        for (i in 0..<parties - 1) {
            while (true) {
                val waiter = waiters[i].value
                if (waiters[i].compareAndSet(waiter, FINISHED)) {
                    if (waiter is KThread) {
                        Parker.unpark(waiter)
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
