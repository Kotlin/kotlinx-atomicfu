package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

class LatchTest {
    private class Arrs(numberOfThreads: Int) {
        val after = atomicArrayOfNulls<Int>(numberOfThreads)
        val before = atomicArrayOfNulls<Int>(numberOfThreads)
        init {repeat(numberOfThreads) {
            after[it].value = 0
            before[it].value = 0
        }}
    }
    @Test
    fun latchTest() {
        repeat(5) { iteration ->
            println("Latch test iteration $iteration")
            repeat(5) {
                val numberOfThreads = it + 2
                val countingDownTo = iteration + 2
                val ar = Arrs(numberOfThreads)
                val latch = CustomCountDownLatch(countingDownTo)
                val countingThread = Fut {
                    repeat(countingDownTo) {
                        sleepMills(Random.nextLong(100))

                        repeat(ar.after.size) { threadToCheck ->
                            if (ar.after[threadToCheck].value != 0) fail("Thread passed latch too early")
                        }

                        latch.countDown()
                    }
                }

                val waiters = List(numberOfThreads) { i -> Fut {
                    sleepMills(Random.nextLong(100))
                    latch.await()
                    ar.after[i].value = 1
                }}

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
    private val waiters = MSQueueLatch<KThread>()

    fun await() {
        val thread = KThread.currentThread()
        waiters.enqueue(thread)
        if (c.value <= 0) return
        Parker.park()
    }

    fun countDown() {
        val myIndex = c.decrementAndGet()
        if (myIndex != 0) return
        while (true) {
            val thread = waiters.dequeue()
            if (thread == null) return
            Parker.unpark(thread)
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
