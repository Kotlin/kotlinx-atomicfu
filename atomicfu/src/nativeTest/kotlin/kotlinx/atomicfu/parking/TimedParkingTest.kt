package kotlinx.atomicfu.parking

import platform.posix.usleep
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime


class TimedParkingTest {
    
    
    @Test
    fun testNanosFirstUnpark500() {
        val parker = ThreadParker(PosixParkingDelegator)

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(500_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 350_000_000)
            assertTrue(t.inWholeNanoseconds < 450_000_000)
            t.inWholeNanoseconds
        }
        
        //sleep is in micros
        usleep(400_000u)
        parker.unpark()

        future1.result
    }

    @Test
    fun testNanosFirstUnpark800() {
        val parker = ThreadParker(PosixParkingDelegator)

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(800_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 650_000_000)
            assertTrue(t.inWholeNanoseconds < 750_000_000)
            t.inWholeNanoseconds
        }

        //sleep is in micros
        usleep(700_000u)
        parker.unpark()

        future1.result
    }

    @Test
    fun testNanosFirstUnpark1200() {
        val parker = ThreadParker(PosixParkingDelegator)

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(1200_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 1050_000_000)
            assertTrue(t.inWholeNanoseconds < 1150_000_000)
            t.inWholeNanoseconds
        }

        //sleep is in micros
        usleep(1100_000u)
        parker.unpark()

        future1.result
    }
    
    @Test
    fun testNanosFirstDeadline500() {
        val parker = ThreadParker(PosixParkingDelegator)

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(500_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 450_000_000)
            assertTrue(t.inWholeNanoseconds < 550_000_000)
            t.inWholeNanoseconds
        }

        usleep(600_000u)
        parker.unpark()

        future1.result
    }

    @Test
    fun testNanosFirstDeadline800() {
        val parker = ThreadParker(PosixParkingDelegator)

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(800_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 750_000_000)
            assertTrue(t.inWholeNanoseconds < 850_000_000)
            t.inWholeNanoseconds
        }

        usleep(900_000u)
        parker.unpark()

        future1.result
    }

    @Test
    fun testNanosFirstDeadline1200() {
        val parker = ThreadParker(PosixParkingDelegator)

        val worker1 = Worker.start()
        val future1 = worker1.execute(TransferMode.UNSAFE, { parker }) { parker ->
            val t = measureTime {
                parker.parkNanos(1200_000_000)
            }
            assertTrue(t.inWholeNanoseconds > 1150_000_000)
            assertTrue(t.inWholeNanoseconds < 1250_000_000)
            t.inWholeNanoseconds
        }

        usleep(1300_000u)
        parker.unpark()

        future1.result
    }
}