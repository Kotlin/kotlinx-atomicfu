package kotlinx.atomicfu.test.parking

import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

class LatchTest {
    @Test
    fun latchTest() {
        repeat(5) { iteration ->
            println("Latch test iteration $iteration")
            repeat(5) {
                val numberOfThreads = it + 2
                val countingDownTo = iteration + 2
                val after = AtomicIntegerArray(numberOfThreads)
                val latch = CustomCountDownLatch(countingDownTo)
                val countingThread = Fut {
                    repeat(countingDownTo) { 
                        Thread.sleep(Random.nextLong(100))
                        
                        repeat(after.length()) { threadToCheck ->
                            if (after.get(threadToCheck) != 0) fail("Thread passed latch too early")
                        }
                        
                        latch.countDown()
                    }
                }
                
                val waiters = List(numberOfThreads) { i -> Fut {
                    Thread.sleep(Random.nextLong(100))
                    latch.await()
                    after.set(i, 1)
                }}
                
                Fut.waitAllAndThrow(waiters + countingThread)

                repeat(after.length()) { threadToCheck ->
                    if (after.get(threadToCheck) != 1) fail("Thread $threadToCheck stuck")
                }
            }
        }
    }
}

class CustomCountDownLatch(count: Int) {
    private val c = AtomicInteger(count)
    private val waiters = MSQueue<KThread>()
    
    fun await() {
        val thread = KThread.currentThread()
        waiters.enqueue(thread)
        if (c.get() <= 0) return
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

private class MSQueue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    fun enqueue(element: E) {
        while (true) {
            val node = Node(element)
            val curTail = tail.get()
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            }
            else tail.compareAndSet(curTail, curTail.next.get())
        }
    }

    fun dequeue(): E? {
        while (true) {
            val currentHead = head.get()
            val currentHeadNext = currentHead.next.get() ?: return null
            if (head.compareAndSet(currentHead, currentHeadNext)) {
                val element = currentHeadNext.element
                currentHeadNext.element = null
                return element
            }
        }
    }
    private class Node<E>(var element: E?) {
        val next = AtomicReference<Node<E>?>(null)
    }
}
