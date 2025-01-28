package kotlinx.atomicfu.parking

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import platform.posix.usleep
import kotlin.native.concurrent.Future
import kotlin.native.concurrent.FutureState
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.fail

@OptIn(ExperimentalStdlibApi::class, ObsoleteWorkersApi::class)
class CyclicBarrierTest {

    private data class CycBarTest(val bar: NativeCyclicBarrier, val before: AtomicArray<Int?>, val after: AtomicArray<Int?>, val myThreadId: Int)
    @Test
    fun simpleBarriertest() {
        repeat(5) { iteration ->
            (5..50 step 5).forEach { numberOfThreads ->
                println("Barrier test iteration $iteration with $numberOfThreads threads")
                val barrier = NativeCyclicBarrier(numberOfThreads)
                val after = atomicArrayOfNulls<Int>(numberOfThreads)
                val before = atomicArrayOfNulls<Int>(numberOfThreads)
                repeat(numberOfThreads) {
                    after[it].value = 0
                    before[it].value = 0
                }
                val threads = List(numberOfThreads) { myThread ->
                    val worker = Worker.start()
                    worker.execute(TransferMode.UNSAFE, { CycBarTest(barrier, before, after, myThread) }) { (bar, bef, aft, myt) ->
                        repeat(aft.size) { otherThread ->
                            if (otherThread != myt && aft[otherThread].value != 0) {
                                fail("Thread $myt arrived too early: $otherThread had value ${aft[otherThread].value}")
                            }
                        }
                        usleep(Random.nextUInt(100_000u))
                        println("Thread $myt ready to wait")
                        bef[myt].value = 1

                        bar.await()

                        aft[myt].value = 1
                        println("Thread $myt finished")

                        repeat(bef.size) { otherThread ->
                            if (bef[otherThread].value == 0) {
                                fail("Thread $myt continued too early: $otherThread had value ${aft[otherThread].value}")
                            }
                        }
                    }
                }
                waitAll(threads)
            }
        }
    }
    
    @Test
    fun stressCyclicBarrier() {
        repeat(5) { iteration ->
            println("Iteration $iteration")
            val threads = mutableListOf<Future<Unit>>()
            val threadSetSize = (iteration + 1) * 5
            val bar = NativeCyclicBarrier(threadSetSize)
            val syncBar = NativeCyclicBarrier(threadSetSize * 5)
            val before = atomicArrayOfNulls<Int>(threadSetSize * 5)
            repeat(before.size) { before[it].value = 0 }
            repeat(threadSetSize * 5) { tId ->
                val t = Worker.start().execute(TransferMode.UNSAFE, { Triple(Pair(bar, syncBar), before, tId) }) { (bars, before, tId) ->
                    repeat(50) {
                        usleep(Random.nextUInt(100_000u))
                        bars.first.await()
                        usleep(Random.nextUInt(100_000u))
                        before[tId].value = before[tId].value?.plus(1)
                        bars.second.await()

                        repeat(before.size) { otherThread ->
                            if (before[otherThread].value!! < before[tId].value!!) {
                                fail("Thread $tId continued too early: $otherThread had value ${before[otherThread].value}")
                            }
                            if (before[otherThread].value!! > before[tId].value!! + 1) {
                                fail("Thread $tId too far behind: $otherThread had value ${before[otherThread].value}")
                            }
                        }
                    }
                }
                threads.add(t)
            }
        waitAll(threads)
        }
    }
    
}

private class NativeCyclicBarrier(private val parties: Int) {
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

internal fun waitAll(futures: Iterable<Future<*>>) {
    while (futures.all{ it.state == FutureState.SCHEDULED }) {
        usleep(100_000u)
    }
    futures.forEach { if (it.state != FutureState.SCHEDULED) it.result }
}
