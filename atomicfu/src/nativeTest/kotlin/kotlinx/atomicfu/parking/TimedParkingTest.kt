package kotlinx.atomicfu.parking

import platform.posix.usleep
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime


class TimedParkingTest {
    
    @Test
    fun testNanosFirstUnpark400() = retry(3) {
        val parker = ThreadParker()

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(600_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 300_000_000)
            assertTrue(t.inWholeNanoseconds < 500_000_000)
            t.inWholeNanoseconds
        }
        
        //sleep is in micros
        usleep(400_000u)
        parker.unpark()

        future1.result
    }

    @Test
    fun testNanosFirstUnpark700() = retry(3) {
        val parker = ThreadParker()

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(900_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 600_000_000)
            assertTrue(t.inWholeNanoseconds < 800_000_000)
            t.inWholeNanoseconds
        }

        //sleep is in micros
        usleep(700_000u)
        parker.unpark()

        future1.result
    }

    @Test
    fun testNanosFirstUnpark1000() = retry(3) {
        val parker = ThreadParker()

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(1200_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
            t.inWholeNanoseconds
        }

        //sleep is in micros
        usleep(1000_000u)
        parker.unpark()

        future1.result
    }
    
    @Test
    fun testNanosFirstDeadline400() = retry(3) {
        val parker = ThreadParker()

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(400_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 300_000_000)
            assertTrue(t.inWholeNanoseconds < 500_000_000)
            t.inWholeNanoseconds
        }

        usleep(600_000u)
        parker.unpark()

        future1.result
    }

    @Test
    fun testNanosFirstDeadline700() = retry(3) {
        val parker = ThreadParker()

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(700_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 600_000_000)
            assertTrue(t.inWholeNanoseconds < 800_000_000)
            t.inWholeNanoseconds
        }

        usleep(900_000u)
        parker.unpark()

        future1.result
    }

    @Test
    fun testNanosFirstDeadline1000() = retry(3) {
        val parker = ThreadParker()

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(1000_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 900_000_000)
            assertTrue(t.inWholeNanoseconds < 1100_000_000)
            t.inWholeNanoseconds
        }

        usleep(1200_000u)
        parker.unpark()

        future1.result
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