package kotlinx.atomicfu.locks

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration

private const val MAX_RANDOM_WAIT_MILLIS = 5L
private const val TEST_ITERATIONS = 50
private val BARRIER_SIZES = (5..50 step 5).toList()
private const val THREADS_PER_BARRIER_SLOT = 5

class CyclicBarrierTest {
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
    fun stressCyclicBarrier() {
        BARRIER_SIZES.forEach { barrierSize ->
            val bar = CyclicBarrier(barrierSize)
            val syncBar = CyclicBarrier(barrierSize * THREADS_PER_BARRIER_SLOT)
            val ar = Arrs(barrierSize * THREADS_PER_BARRIER_SLOT)
            val threads = List(barrierSize * THREADS_PER_BARRIER_SLOT) { tId ->
                Fut {
                    repeat(TEST_ITERATIONS) { internalIteration ->
                        sleepMillis(Random.nextLong(MAX_RANDOM_WAIT_MILLIS))
                        bar.await()
                        sleepMillis(Random.nextLong(MAX_RANDOM_WAIT_MILLIS))
                        val newN = ar.before[tId].value + 1
                        ar.before[tId].value = newN
                        syncBar.await()
                        repeat(ar.before.size) { otherThread ->
                            if (ar.before[otherThread].value < newN) {
                                fail("Thread $tId (value: $newN, id: ${ParkingSupport.currentThreadHandle()}) continued too early: $otherThread had value ${ar.before[otherThread].value}")
                            }
                            if (ar.before[otherThread].value > newN + 1) {
                                fail("Thread $tId (value: $newN, id: ${ParkingSupport.currentThreadHandle()}) too far behind: $otherThread had value ${ar.before[otherThread].value}")
                            }
                        }
                    }
                }
            }
            Fut.waitAllAndThrow(threads)
        }
    }
}

private class HandleWrapper(val handle: ParkingHandle) {
    val woken = atomic(false)
}

private class CyclicBarrier(private val parties: Int) {
    private val queue = MSQueueCyclicBarrier<HandleWrapper>()

    fun await() {
        val wrapper = HandleWrapper(ParkingSupport.currentThreadHandle())
        val n = queue.enqueue(wrapper)
        if (n % parties == 0L) {
            var wokenUp = 0
            while (wokenUp < parties - 1) {
                val deq = queue.dequeue()
                if (deq == null) fail("Not enough parties enqueued")
                if (deq.first % parties == 0L) continue
                if (deq.second.woken.compareAndSet(false, true)) {
                    ParkingSupport.unpark(deq.second.handle)
                    wokenUp++
                }
            }
        } else {
            while (!wrapper.woken.value) {
                ParkingSupport.park(Duration.INFINITE)
            }
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
                val _ = tail.compareAndSet(curTail, node)
                return node.id
            } else {
                val _ = tail.compareAndSet(curTail, curTail.next.value!!)
            }
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
