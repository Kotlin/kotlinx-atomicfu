package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

class CyclicBarrierTest {
    private class Arrs(numberOfThreads: Int) {
        val after = atomicArrayOfNulls<Int>(numberOfThreads)
        val before = atomicArrayOfNulls<Int>(numberOfThreads)
        init {repeat(numberOfThreads) {
            after[it].value = 0
            before[it].value = 0
        }}
    }
    @Test
    fun simpleBarriertest() {
        repeat(5) { iteration ->
            println("Barrier test iteration $iteration")
            (5..50 step 5).forEach { numberOfThreads ->
                val barrier = JavaCyclicBarrier(numberOfThreads)
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
    
    @Test
    fun debugTest() {
        val threads = mutableListOf<Fut>()
//        val threadSetSize = 1
//        val bar = JavaCyclicBarrier(threadSetSize)
        val syncBar = JavaCyclicBarrier(2)
        val ar = Arrs(2)
        repeat(2) { tId ->
            val t = Fut {
                repeat(50) { internalIteration ->
//                    sleepMills(Random.nextLong(100))
//                    bar.await()
                    sleepMills(Random.nextLong(100))
                    val newN = ar.before[tId].value!! + 1
                    ar.before[tId].value = newN
                    syncBar.await()
                    repeat(ar.before.size) { otherThread ->
                        if (ar.before[otherThread].value!! < newN) {
                            fail("Thread $tId (value: $newN, id: ${currentThreadId()}) continued too early: $otherThread had value ${ar.before[otherThread].value!!}")
                        }
                        if (ar.before[otherThread].value!! > newN + 1) {
                            fail("Thread $tId (value: $newN, id: ${currentThreadId()}) too far behind: $otherThread had value ${ar.before[otherThread].value!!}")
                        }
                    }
                }
            }
            threads.add(t)
        }
        Fut.waitAllAndThrow(threads)
    }

    @Test
    fun stressCyclicBarrier() {
        repeat(5) { iteration ->
            println("Stress test $iteration")
            val threads = mutableListOf<Fut>()
            val threadSetSize = (iteration + 1) * 5
            val bar = JavaCyclicBarrier(threadSetSize)
            val syncBar = JavaCyclicBarrier(threadSetSize * 5)
            val ar = Arrs(threadSetSize * 5)
            repeat(threadSetSize * 5) { tId ->
                val t = Fut {
                    repeat(50) { internalIteration ->
                        sleepMills(Random.nextLong(100))
                        bar.await()
                        sleepMills(Random.nextLong(100))
                        val newN = ar.before[tId].value!! + 1
                        ar.before[tId].value = newN
                        syncBar.await()
                        repeat(ar.before.size) { otherThread ->
                            if (ar.before[otherThread].value!! < newN) {
                                fail("Thread $tId (value: $newN, id: ${KThread.currentThread()}) continued too early: $otherThread had value ${ar.before[otherThread].value!!}")
                            }
                            if (ar.before[otherThread].value!! > newN + 1) {
                                fail("Thread $tId (value: $newN, id: ${KThread.currentThread()}) too far behind: $otherThread had value ${ar.before[otherThread].value!!}")
                            }
                        }
                    }
                }
                threads.add(t)
            }
            Fut.waitAllAndThrow(threads)
        }
    }
}

private class JavaCyclicBarrier(private val parties: Int) {
    private val queue = MSQueueCyclicBarrier<KThread>()

    fun await() {
        val n = queue.enqueue(KThread.Companion.currentThread())
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
