package kotlinx.atomicfu.locks

import kotlinx.atomicfu.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration

private const val TEST_ITERATIONS = 5
private const val MAX_RANDOM_WAIT_MILLIS = 5L
private val THREAD_SETS = listOf(2, 5, 7)

class LatchTest {
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
    fun latchTest() {
        repeat(TEST_ITERATIONS) { iteration ->
            THREAD_SETS.forEach { numberOfThreads ->
                val countingDownTo = iteration + 2
                val ar = Arrs(numberOfThreads)
                val latch = CustomCountDownLatch(countingDownTo)
                val countingThread = Fut {
                    repeat(countingDownTo) {
                        sleepMillis(Random.nextLong(MAX_RANDOM_WAIT_MILLIS))

                        repeat(ar.after.size) { threadToCheck ->
                            if (ar.after[threadToCheck].value != 0) fail("Thread passed latch too early")
                        }

                        latch.countDown()
                    }
                }

                val waiters = List(numberOfThreads) { i ->
                    Fut {
                        sleepMillis(Random.nextLong(MAX_RANDOM_WAIT_MILLIS))
                        latch.await()
                        ar.after[i].value = 1
                    }
                }

                Fut.waitAllAndThrow(waiters + countingThread)

                repeat(ar.after.size) { threadToCheck ->
                    if (ar.after[threadToCheck].value != 1) fail("Thread $threadToCheck stuck")
                }
            }
        }
    }
}

class CustomCountDownLatch(count: Int) {
    private val c = atomic(count)
    private val waiters = MPSCQueueLatch<ParkingHandle>()

    fun await() {
        val thread = ParkingSupport.currentThreadHandle()
        if (c.value <= 0) return
        if (waiters.enqueue(thread)) {
            while (c.value > 0) {
                ParkingSupport.park(Duration.INFINITE)
            }
        }
    }

    fun countDown() {
        val myIndex = c.decrementAndGet()
        if (myIndex != 0) return
        waiters.drain { ParkingSupport.unpark(it) }
    }
}

private class MPSCQueueLatch<E> {
    private val head = Node<E>(null)
    private val tail = atomic<Node<E>?>(head) // if null, then closed

    fun enqueue(element: E): Boolean {
        val node = Node(element)
        tail.loop {
            if (it == null) return false
            if (it.next.compareAndSet(null, node)) {
                tail.compareAndSet(it, node)
                return true
            } else {
                tail.compareAndSet(it, it.next.value!!)
            }
        }
    }

    fun drain(action: (E) -> Unit) {
        close()
        var node = head.next.value
        while (node != null) {
            action(node.element!!)
            node = node.next.value
        }
        head.next.value = null
    }

    private fun close() {
        tail.loop {
            if (tail.compareAndSet(it, null)) return
        }
    }

    private class Node<E>(var element: E?) {
        val next = atomic<Node<E>?>(null)
    }
}
