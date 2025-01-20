import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.*

class BarrierTest {
    @Test
    fun testBarrier() {
        repeat(5) { iteration ->
            repeat(5) {
                val numberOfThreads = it + 2
                println("Barrier test iteration $iteration with $numberOfThreads threads")
                val barrier = PureJavaBarrier(numberOfThreads)
                val before = AtomicIntegerArray(numberOfThreads)
                val after = AtomicIntegerArray(numberOfThreads)
                val threads = List(numberOfThreads) { myThread ->
                    thread(name = "MyThread-$myThread") {
                        println("Thread $myThread started")
                        repeat(numberOfThreads) { otherThread ->
                            if (otherThread != myThread && after.get(otherThread) != 0) {
                                fail("Thread $myThread arrived too early")
                            }
                        }
                        Thread.sleep(Random.nextLong(100))
                        println("Thread $myThread ready to wait")
                        before.set(myThread, 1)
                        
                        barrier.await()
                        
                        after.set(myThread, 1)
                        println("Thread $myThread finished")
                        
                        repeat(numberOfThreads) { otherThread ->
                            if (before.get(otherThread) == 0) {
                                fail("Thread $myThread continued too early: $otherThread had value ${before.get(otherThread)}")
                            }
                        }
                    }
                }
                threads.forEach { it.join() }
            }
        }
    }
}

/**
 * Single-use barrier that blocks all participants until they all arrive.
 */
class PureJavaBarrier(private val parties: Int) {
    init {
        require(parties > 1)
    }
    private val count = AtomicInteger(0)
    private val waiters = AtomicReferenceArray<Any?>(parties - 1)

    fun await() {
        val myIndex = count.getAndIncrement()
        if (myIndex == parties - 1) {
            wakeUpEveryone()
            return
        }
        val currentThread = KThread.currentThread()
        while (true) {
            val waiter = waiters[myIndex]
            when {
                waiter === null -> waiters.compareAndSet(myIndex, null, currentThread)
                waiter === FINISHED -> return
                else -> Parker.park()
            }
        }
    }

    private fun wakeUpEveryone() {
        for (i in 0..<parties - 1) {
            while (true) {
                val waiter = waiters[i]
                if (waiters.compareAndSet(i, waiter, FINISHED)) {
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