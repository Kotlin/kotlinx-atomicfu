package kotlinx.atomicfu.test.parking

import junit.framework.TestCase.assertTrue
import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.time.measureTime

class ThreadParkerTest {

    @Test
    fun parkUnpark() {
        val mainThread = KThread.currentThread()

        thread {
            sleep(1000)
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

        thread {
            Parker.unpark(mainThread)
        }

        sleep(1000)
        Parker.park() 

        assertTrue(true)
    }
}
