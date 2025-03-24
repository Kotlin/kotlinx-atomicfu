package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.IllegalStateException
import kotlin.time.Duration.Companion.nanoseconds

class TimedParkingTest {

    @Test
    fun testNanosFirstUnpark400() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(600_000_000.nanoseconds)
            }
            assertTrue(t.inWholeNanoseconds > 300_000_000)
            assertTrue(t.inWholeNanoseconds < 500_000_000)
        }

        sleepMills(400)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstUnpark700() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(900_000_000.nanoseconds)
            }
            assertTrue(t.inWholeNanoseconds > 600_000_000)
            assertTrue(t.inWholeNanoseconds < 800_000_000)
        }

        sleepMills(700)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstUnpark1000() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(1200_000_000.nanoseconds)
            }
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
        }

        sleepMills(1000)
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
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
        }

        sleepMills(1000)
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
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
        }

        sleepMills(1000)
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
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
        }

        sleepMills(1000)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline400() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(400_000_000.nanoseconds)
            }
            assertTrue(t.inWholeNanoseconds > 300_000_000)
            assertTrue(t.inWholeNanoseconds < 500_000_000)
        }

        sleepMills(600)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline700() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(700_000_000.nanoseconds)
            }
            assertTrue(t.inWholeNanoseconds > 600_000_000)
            assertTrue(t.inWholeNanoseconds < 800_000_000)
        }

        sleepMills(900)
        ParkingSupport.unpark(handle1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline1200() = retry(3) {
        var handle1: ParkingHandle? = null

        val thread1 = Fut {
            handle1 = ParkingSupport.currentThreadHandle()
            val t = measureTime {
                ParkingSupport.park(1000_000_000.nanoseconds)
            }
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
        }

        sleepMills(1200)
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
        val err = IllegalStateException("Failed after $times retries")
        err.addSuppressed(lastThrowable?: IllegalStateException("Retry failed but no error"))
        throw err
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

    fun throwIfError() = atomicError.value?.let { throw it }

    companion object {
        fun waitAllAndThrow(futs: List<Fut>) {
            while(futs.any { !it.done.value }) {
                sleepMills(1000)
                futs.forEach { it.throwIfError() }
            }
        }
    }

}
