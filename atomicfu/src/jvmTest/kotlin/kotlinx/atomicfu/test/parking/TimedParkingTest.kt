package kotlinx.atomicfu.test.parking

import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.IllegalStateException

class TimedParkingTest {


    @Test
    fun testNanosFirstUnpark400() = retry(3) {
        var kthread1: KThread? = null

        val thread1 = Fut {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(600_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 300_000_000)
            assertTrue(t.inWholeNanoseconds < 500_000_000)
            t.inWholeNanoseconds
        }

        sleep(400)
        Parker.unpark(kthread1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstUnpark700() = retry(3) {
        var kthread1: KThread? = null

        val thread1 = Fut {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(900_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 600_000_000)
            assertTrue(t.inWholeNanoseconds < 800_000_000)
            t.inWholeNanoseconds
        }

        sleep(700)
        Parker.unpark(kthread1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstUnpark1000() = retry(3) {
        var kthread1: KThread? = null

        val thread1 = Fut {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(1200_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
            t.inWholeNanoseconds
        }

        sleep(1000)
        Parker.unpark(kthread1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline400() = retry(3) {
        var kthread1: KThread? = null

        val thread1 = Fut {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(400_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 300_000_000)
            assertTrue(t.inWholeNanoseconds < 500_000_000)
            t.inWholeNanoseconds
        }

        sleep(600)
        Parker.unpark(kthread1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline700() = retry(3) {
        var kthread1: KThread? = null

        val thread1 = Fut {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(700_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 600_000_000)
            assertTrue(t.inWholeNanoseconds < 800_000_000)
            t.inWholeNanoseconds
        }

        sleep(900)
        Parker.unpark(kthread1!!)

        thread1.waitThrowing()
    }

    @Test
    fun testNanosFirstDeadline1200() = retry(3) {
        var kthread1: KThread? = null

        val thread1 = Fut {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(1000_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
            t.inWholeNanoseconds
        }

        sleep(1200)
        Parker.unpark(kthread1!!)

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


internal class Fut(private val block: () -> Unit) {
    private var thread: Thread? = null
    private val atomicError = AtomicReference<Throwable?>(null)
    val done = AtomicBoolean(false)
    init {
        val th = thread {
            try { block() } 
            catch (t: Throwable) {
                atomicError.set(t)
                throw t
            }
            finally { done.set(true) }
        }
        thread = th
    }
    fun waitThrowing() {
        thread!!.join()
        throwIfError()
    }
    
    fun throwIfError() = atomicError.get()?.let { throw it }
    
    companion object {
        fun waitAllAndThrow(futs: List<Fut>) {
            while(futs.any { !it.done.get() }) {
                sleep(1000)
                futs.forEach { it.throwIfError() }
            }
        }
    }
    
}
