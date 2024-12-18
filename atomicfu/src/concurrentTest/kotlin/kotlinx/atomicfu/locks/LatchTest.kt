package kotlinx.atomicfu.locks

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
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
        init {repeat(numberOfThreads) {
            after[it].value = 0
            before[it].value = 0
        }}
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
    private val waiters = MSQueueLatch<ParkingHandle>()

    fun await() {
        val thread = ParkingSupport.currentThreadHandle()
        waiters.enqueue(thread)
        while (c.value > 0) ParkingSupport.park(Duration.INFINITE)
    }

    fun countDown() {
        val myIndex = c.decrementAndGet()
        if (myIndex != 0) return
        while (true) {
            val thread = waiters.dequeue()
            if (thread == null) return
            ParkingSupport.unpark(thread)
        }
    }
}

private class MSQueueLatch<E> {
    private val head = atomic(Node<E>(null))
    private val tail = atomic(head.value)

    fun enqueue(element: E) {
        while (true) {
            val node = Node(element)
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            }
            else tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }

    fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value ?: return null
            if (head.compareAndSet(currentHead, currentHeadNext)) {
                val element = currentHeadNext.element
                currentHeadNext.element = null
                return element
            }
        }
    }
    private class Node<E>(var element: E?) {
        val next = atomic<Node<E>?>(null)
    }
}
