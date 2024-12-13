package kotlinx.atomicfu.locks

import kotlin.native.concurrent.Future
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Compares to the atomicfu implementation.
 * 
 * Fair tests: 
 * Each thread has a number id, n threads have numbers 0 until n.
 * A counter protected by mutex needs to be incremented unttil 10.000
 * A thread can only increment when the counter is counter mod id. This tests fariness and progress. For each thread.
 * 
 * Random tests: 
 * Are like fair tests but after each increment the thread choses randomly which thread should be next.
 * This loses fairness but still requires progress for the test to complete.
 */
class CompareToAtomicFU {
    @Test
    fun compareWithAtomicFUSingleThread() {
        var accumulatedDifference = 0L
        repeat(3) {
            val time1 = measureTime {
                singleTOld()
            }
            println("Old $time1")
            val time2 = measureTime {
                singleTNew()
            }
            println("New $time2")
            accumulatedDifference += time1.toLong(DurationUnit.MILLISECONDS) - time2.toLong(DurationUnit.MILLISECONDS)
        }
        assertTrue(accumulatedDifference > 0)
    }

    @Test
    fun compareAtomicFU3ThreadsFair() = compareAtomicFUMultiThread(3, true)

    @Test
    fun compareAtomicFU5ThreadsFair() = compareAtomicFUMultiThread(5, true)

    @Test
    fun compareAtomicFU7ThreadsFair() = compareAtomicFUMultiThread(7, true)

    @Test
    fun compareAtomicFU3ThreadsRandom() = compareAtomicFUMultiThread(3, false)

    @Test
    fun compareAtomicFU5ThreadsRandom() = compareAtomicFUMultiThread(5, false)

    @Test
    fun compareAtomicFU7ThreadsRandom() = compareAtomicFUMultiThread(7, false)
    
    fun compareAtomicFUMultiThread(nThreads: Int, fair: Boolean) {
        var accumulatedDifference = 0L
        repeat(3) {
            val timeNew = measureTime {
                val newLock = NewLockInt()
                mulitTestLock(newLock, nThreads, fair)
            }
            println("New $timeNew")

            val timeOld = measureTime {
                val oldLock = OldLockInt()
                mulitTestLock(oldLock, nThreads, fair)
            }
            println("Old $timeOld")
            accumulatedDifference += timeOld.toLong(DurationUnit.MILLISECONDS) - timeNew.toLong(DurationUnit.MILLISECONDS)
        }
        assertTrue(accumulatedDifference > 0)
    }

    fun singleTNew() {
        val nativeMutex = NativeMutex { PosixParkingDelegator }
        repeat(1000000) {
            nativeMutex.lock()
            nativeMutex.unlock()
        }
    }

    fun singleTOld() {
        val reentrantLock = SynchronizedObjectOld()
        repeat(1000000) {
            reentrantLock.lock()
            reentrantLock.unlock()
        }
    }

    fun mulitTestLock(lockInt: LockInt, nThreads: Int, fair: Boolean) {
        val countTo = 100000
        val futureList = mutableListOf<Future<Unit>>()
        repeat(nThreads) { i ->
            val test = LockIntTest(lockInt, countTo, nThreads, i, fair)
            futureList.add(testWithWorker(test))
        }
        futureList.forEach {
            it.result
        }
    }

    fun testWithWorker(test: LockIntTest): Future<Unit> {
        val worker = Worker.start()
        return worker.execute(TransferMode.UNSAFE, { test }) { t ->
            while (true) {
                t.lockInt.lock()
                    if (t.fair && t.lockInt.n % t.mod == t.id) t.lockInt.n++
                    if (!t.fair && t.lockInt.rand == t.id) {
                        t.lockInt.n++
                        t.lockInt.rand = (0..< t.mod).random()
                    }
                    if (t.lockInt.n >= t.max) {
                        t.lockInt.unlock()
                        break
                    }
                t.lockInt.unlock()
            }
        }
    }

    data class LockIntTest(
        val lockInt: LockInt,
        val max: Int,
        val mod: Int,
        val id: Int,
        val fair: Boolean
    )

    class NewLockInt: LockInt{
        private val lock = NativeMutex { PosixParkingDelegator }
        override var n = 0
        override var rand = 0
        override fun lock() = lock.lock()
        override fun unlock() = lock.unlock()
    }

    class OldLockInt: LockInt {
        private val lock = SynchronizedObjectOld()
        override var n = 0
        override var rand = 0
        override fun lock() = lock.lock()
        override fun unlock() = lock.unlock()
    }

    interface LockInt {
        fun lock()
        fun unlock()
        var n: Int
        var rand: Int
    }
}
