package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.IllegalStateException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class TimedParkingTest {

    @Test
    fun testNanosFirstUnpark400() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(600.milliseconds)
            }
            assertTrue(t.inWholeMilliseconds > 300)
            assertTrue(t.inWholeMilliseconds < 500)
        }

        sleepMillis(400)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstUnpark700() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(900.milliseconds)
            }
            assertTrue(t.inWholeMilliseconds > 600)
            assertTrue(t.inWholeMilliseconds < 800)
        }

        sleepMillis(700)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstUnpark1000() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(1200.milliseconds)
            }
            assertTrue(t.inWholeMilliseconds > 900)
            assertTrue(t.inWholeMilliseconds < 1100)
        }

        sleepMillis(1000)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }
    
    @Test
    fun testNanosFirstUnparkLongMax() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(Long.MAX_VALUE.nanoseconds)
            }
            assertTrue(t.inWholeMilliseconds > 900)
            assertTrue(t.inWholeMilliseconds < 1100)
        }

        sleepMillis(1000)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }
    
    @Test
    fun testNanosFirstUnparkIntMax() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(Int.MAX_VALUE.toLong().nanoseconds)
            }
            assertTrue(t.inWholeMilliseconds > 900)
            assertTrue(t.inWholeMilliseconds < 1100)
        }

        sleepMillis(1000)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }
    
    @Test
    fun testNanosFirstUnpark3rdLong() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park((Long.MAX_VALUE / 3).nanoseconds)
            }
            assertTrue(t.inWholeMilliseconds > 900)
            assertTrue(t.inWholeMilliseconds < 1100)
        }

        sleepMillis(1000)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline400() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(400.milliseconds)
            }
            assertTrue(t.inWholeMilliseconds > 300)
            assertTrue(t.inWholeMilliseconds < 500)
        }

        sleepMillis(600)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline700() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(700.milliseconds)
            }
            assertTrue(t.inWholeMilliseconds > 600)
            assertTrue(t.inWholeMilliseconds < 800)
        }

        sleepMillis(900)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline1200() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(1000.milliseconds)
            }
            assertTrue(t.inWholeMilliseconds > 900)
            assertTrue(t.inWholeMilliseconds < 1100)
        }

        sleepMillis(1200)
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
