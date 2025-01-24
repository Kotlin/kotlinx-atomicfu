package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic
import platform.posix.usleep
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.fail

class LatchTest {
    @Test
    fun latchTest() {
        repeat(5) { iteration ->
            repeat(5) {
                val numberOfThreads = it + 2
                val countingDownTo = iteration + 2
                val after = atomicArrayOfNulls<Int>(numberOfThreads)
                repeat(after.size) { after[it].value = 0 }
                println("Latch test iteration $iteration with $numberOfThreads threads")
                val latch = CustomCountDownLatch(countingDownTo)
                val countingThread = Worker.start().execute(TransferMode.UNSAFE, { Triple(latch, after, countingDownTo)}) { (l, a, c) ->
                    repeat(c) {
                        usleep(Random.nextUInt(100_000u))

                        repeat(a.size) { threadToCheck ->
                            if (a[threadToCheck].value != 0) fail("Thread passed latch too early")
                        }

                        l.countDown()
                    }
                }

                val waiters = List(numberOfThreads) { i ->
                    Worker.start().execute(TransferMode.UNSAFE, { Triple(latch, after, i)}) { (l, a, i) ->
                        usleep(Random.nextUInt(100_000u))
                    l.await()
                    a[i].value = 1
                }}

                countingThread.result
                waiters.forEach { it.result }
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
