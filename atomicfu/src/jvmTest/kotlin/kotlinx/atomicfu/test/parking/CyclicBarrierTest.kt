package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

@OptIn(ExperimentalStdlibApi::class)
class CyclicBarrierTest {

    @Test
    fun simpleBarriertest() {
        repeat(5) { iteration ->
            (5..50 step 5).forEach { numberOfThreads ->
                println("Barrier test iteration $iteration with $numberOfThreads threads")
                val barrier = JavaCyclicBarrier(numberOfThreads)
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
                threads.forEach { it.join(5000) }
            }
        }
    }
    
    @Test
    fun stressCyclicBarrier() {
        repeat(5) { iteration ->
            println("Iteration $iteration")
            val threads = mutableListOf<Thread>()
            val threadSetSize = (iteration + 1) * 5
            val bar = JavaCyclicBarrier(threadSetSize)
            val syncBar = JavaCyclicBarrier(threadSetSize * 5)
            val before = AtomicIntegerArray(threadSetSize * 5)
            repeat(threadSetSize * 5) { tId ->
                val t = thread { 
                    repeat(50) { internalIteration ->
                        Thread.sleep(Random.nextLong(100))
                        bar.await()
                        Thread.sleep(Random.nextLong(100))
                        val newN = before.get(tId) + 1
                        before.set(tId, newN)
                        syncBar.await()
                        repeat(before.length()) { otherThread ->
                            if (before.get(otherThread) < internalIteration) {
                                fail("Thread $tId continued too early: $otherThread had value ${before.get(otherThread)}")
                            }
                            if (before.get(otherThread) > internalIteration + 1) {
                                fail("Thread $tId too far behind: $otherThread had value ${before.get(otherThread)}")
                            }
                        }
                    }
                    println("Thread finished")
                }
                threads.add(t)
            }
            threads.forEach { it.join() }
        }
    }
}

private class JavaCyclicBarrier(private val parties: Int) {
    private val queue = MSQueueCyclicBarrier<KThread>()

    fun await() {
        val n = queue.enqueue(KThread.currentThread())
        if (n % parties == 0L) {
            var wokenUp = 0
            while (wokenUp < parties - 1) {
                val deq = queue.dequeue()
                if (deq == null) fail("Not enough parties enqueued")
                if (deq.first % parties == 0L) continue
                Parker.unpark(deq.second)
                wokenUp++
            }
        } else {
            Parker.park()
        }
    }
}


private class MSQueueCyclicBarrier<E> {
    private val head = atomic(Node<E>(null, 0))
    private val tail = atomic(head.value)

    fun enqueue(element: E): Long {
        while (true) {
            val curTail = tail.value
            val node = Node(element, curTail.id + 1)
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return node.id
            }
            else tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }

    fun dequeue(): Pair<Long, E>? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value ?: return null
            if (head.compareAndSet(currentHead, currentHeadNext)) {
                val element = currentHeadNext.element
                currentHeadNext.element = null
                val id = currentHeadNext.id
                return element?.let { Pair(id, it) }
            }
        }
    }
    private class Node<E>(var element: E?, val id: Long) {
        val next = atomic<Node<E>?>(null)
    }
}
