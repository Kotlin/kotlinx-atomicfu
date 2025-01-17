package kotlinx.atomicfu.test.parking

import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import java.lang.Thread.sleep

class TimedParkingTest {


    @Test
    fun testNanosFirstUnpark500() {
        var kthread1: KThread? = null

        val thread1 = thread {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(500_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 350_000_000)
            assertTrue(t.inWholeNanoseconds < 450_000_000)
            t.inWholeNanoseconds
        }

        //sleep is in micros
        sleep(400)
        Parker.unpark(kthread1!!)

        thread1.join()
    }

    @Test
    fun testNanosFirstUnpark800() {
        var kthread1: KThread? = null

        val thread1 = thread {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(800_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 650_000_000)
            assertTrue(t.inWholeNanoseconds < 750_000_000)
            t.inWholeNanoseconds
        }

        //sleep is in micros
        sleep(700)
        Parker.unpark(kthread1!!)

        thread1.join()
    }

    @Test
    fun testNanosFirstUnpark1200() {
        var kthread1: KThread? = null

        val thread1 = thread {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(1200_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 1050_000_000)
            assertTrue(t.inWholeNanoseconds < 1150_000_000)
            t.inWholeNanoseconds
        }

        //sleep is in micros
        sleep(1100)
        Parker.unpark(kthread1!!)

        thread1.join()
    }

    @Test
    fun testNanosFirstDeadline500() {
        var kthread1: KThread? = null

        val thread1 = thread {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(500_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 450_000_000)
            assertTrue(t.inWholeNanoseconds < 550_000_000)
            t.inWholeNanoseconds
        }

        sleep(600)
        Parker.unpark(kthread1!!)

        thread1.join()
    }

    @Test
    fun testNanosFirstDeadline800() {
        var kthread1: KThread? = null

        val thread1 = thread {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(800_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 750_000_000)
            assertTrue(t.inWholeNanoseconds < 850_000_000)
            t.inWholeNanoseconds
        }

        sleep(900)
        Parker.unpark(kthread1!!)

        thread1.join()
    }

    @Test
    fun testNanosFirstDeadline1200() {
        var kthread1: KThread? = null

        val thread1 = thread {
            kthread1 = KThread.currentThread()
            val t = measureTime {
                Parker.parkNanos(1200_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 1150_000_000)
            assertTrue(t.inWholeNanoseconds < 1250_000_000)
            t.inWholeNanoseconds
        }

        sleep(1300)
        Parker.unpark(kthread1!!)

        thread1.join()
    }
}
