package kotlinx.atomicfu.parking

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

class ThreadParkerTest {

    @Test
    fun parkUnpark() {
        val mainThread = KThread.currentThread()

        testThread {
            sleepMills(1000)
            Parker.unpark(mainThread)
        }

        val time = measureTime {
            Parker.park()
        }

        assertTrue(time.inWholeMilliseconds > 900)
    }

    @Test
    fun unparkPark() {
        val mainThread = KThread.currentThread()

        testThread {
            Parker.unpark(mainThread)
        }

        sleepMills(1000)
        Parker.park()

        assertTrue(true)
    }
}
