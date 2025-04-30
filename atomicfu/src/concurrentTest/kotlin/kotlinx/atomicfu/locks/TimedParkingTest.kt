package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.IllegalStateException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class TimedParkingTest {

    @Test
    fun testFirstUnpark400() = testFirstUnpark(400)
    @Test
    fun testFirstUnpark700() = testFirstUnpark(700)
    @Test
    fun testFirstUnpark1000() = testFirstUnpark(1000)
    @Test
    fun testFirstUnparkLongMax() = testFirstUnpark(1000, Long.MAX_VALUE)
    @Test
    fun testFirstUnpark3rdLong() = testFirstUnpark(1000, Long.MAX_VALUE / 3)

    @Test
    fun testFirstTimeout400() = testFirstTimeout(400)
    @Test
    fun testFirstTimeout700() = testFirstTimeout(700)
    @Test
    fun testFirstTimeout1200() = testFirstTimeout(1200)

    @Test
    fun testFirstDeadline400() = testFirstDeadline(400)
    @Test
    fun testFirstDeadline700() = testFirstDeadline(700)
    @Test
    fun testFirstDeadline1200() = testFirstDeadline(1200)
    
    private fun testFirstTimeout(timeOutMillis: Long) = retry(3) {
        val thread1 = Fut {
            val t = measureTime { ParkingSupport.park(timeOutMillis.milliseconds) }
            assertTrue(t.inWholeMilliseconds > timeOutMillis - 50)
        }
        thread1.waitThrowing()
    }
    
    private fun testFirstDeadline(timeOutMillis: Long) = retry(3) {
        val thread1 = Fut {
            val mark = TimeSource.Monotonic.markNow() + timeOutMillis.milliseconds
            val t = measureTime { ParkingSupport.parkUntil(mark) }
            assertTrue(t.inWholeMilliseconds > timeOutMillis - 50)
        }
        thread1.waitThrowing()
    }

    private fun testFirstUnpark(unparkAfterMillis: Long, parkForMillis: Long = unparkAfterMillis + 500) = retry(3) {
        var handle1: ParkingHandle? = null
        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime { ParkingSupport.park((parkForMillis).milliseconds) }
            assertTrue(t.inWholeMilliseconds < parkForMillis )
        }

        sleepMillis(unparkAfterMillis)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    private fun retry(times: Int, block: () -> Unit): Unit {
        var lastThrowable: Throwable? = null
        repeat(times) {
            try {
                return block()
            } catch (t: Throwable) {
                lastThrowable = t
            }
        }
        lastThrowable?.let { 
            throw IllegalStateException("Failed after $times retries").apply { addSuppressed(it) } 
        }
    }
}


internal class Fut(block: () -> Unit) {
    private val done = atomic(false)
    private val atomicError = atomic<Throwable?>(null)
    private val thread: TestThread = testThread {
        try {
            block()
        } catch (t: Throwable) {
            atomicError.value = t
            throw t
        } finally {
            done.value = true
        }
    }

    fun waitThrowing() {
        thread.join()
        throwIfError()
    }

    private fun throwIfError() = atomicError.value?.let { throw it }

    companion object {
        fun waitAllAndThrow(futs: List<Fut>) {
            var remainingFuts = futs
            while (remainingFuts.isNotEmpty()) {
                remainingFuts.forEach { it.throwIfError() }
                remainingFuts = remainingFuts.filter { !it.done.value }
                sleepMillis(50)
            }
        }
    }
}
