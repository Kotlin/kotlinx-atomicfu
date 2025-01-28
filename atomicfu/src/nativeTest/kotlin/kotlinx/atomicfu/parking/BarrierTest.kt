@file:OptIn(ObsoleteWorkersApi::class)

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import kotlinx.atomicfu.parking.waitAll
import kotlin.random.Random
import kotlin.test.*
import platform.posix.usleep
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.random.nextUInt

class BarrierTest {
    data class BarTest(val bar: NativeBarrier, val before: AtomicArray<Int?>, val after: AtomicArray<Int?>, val myThreadId: Int)
    @Test
    fun testBarrier() {
        repeat(5) { iteration ->
            repeat(5) {
                val numberOfThreads = it + 2
                println("Barrier test iteration $iteration with $numberOfThreads threads")
                val barrier = NativeBarrier(numberOfThreads)
                val after = atomicArrayOfNulls<Int>(numberOfThreads)
                val before = atomicArrayOfNulls<Int>(numberOfThreads)
                repeat(numberOfThreads) { 
                    after[it].value = 0 
                    before[it].value = 0
                }
                val threads = List(numberOfThreads) { myThread ->
                    val worker = Worker.start()
                    worker.execute(TransferMode.UNSAFE, { BarTest(barrier, before, after, myThread) }) { (bar, bef, aft, myt) ->
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
}

/**
 * Single-use barrier that blocks all participants until they all arrive.
 */
class NativeBarrier(private val parties: Int) {
    init {
        require(parties > 1)
    }
    private val count = atomic(0)
    private val waiters = atomicArrayOfNulls<Any?>(parties - 1)

    fun await() {
        val myIndex = count.getAndIncrement()
        if (myIndex == parties - 1) {
            wakeUpEveryone()
            return
        }
        val currentThread = KThread.currentThread()
        while (true) {
            val waiter = waiters[myIndex].value
            when {
                waiter === null -> waiters[myIndex].compareAndSet(null, currentThread)
                waiter === FINISHED -> return
                else -> Parker.park()
            }
        }
    }

    private fun wakeUpEveryone() {
        for (i in 0..<parties - 1) {
            while (true) {
                val waiter = waiters[i].value
                if (waiters[i].compareAndSet(waiter, FINISHED)) {
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