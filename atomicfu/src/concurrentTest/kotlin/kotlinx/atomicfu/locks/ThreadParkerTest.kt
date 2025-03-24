package kotlinx.atomicfu.locks

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.measureTime

class ThreadParkerTest {

    @Test
    fun parkUnpark() {
        val mainThread = ParkingSupport.currentThreadHandle()

        testThread {
            sleepMills(1000)
            ParkingSupport.unpark(mainThread)
        }

        val time = measureTime {
            ParkingSupport.park(Duration.INFINITE)
        }

        assertTrue(time.inWholeMilliseconds > 900)
    }

    @Test
    fun unparkPark() {
        val mainThread = ParkingSupport.currentThreadHandle()

        testThread {
            ParkingSupport.unpark(mainThread)
        }

        sleepMills(1000)
        ParkingSupport.park(Duration.INFINITE)

        assertTrue(true)
    }
}