package kotlinx.atomicfu.test.parking

import junit.framework.TestCase.assertTrue
import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import kotlinx.atomicfu.parking.currentThreadId
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.test.Test

class ThreadParkerTest {

    @Test
    fun parkUnpark() {
        println("Started Main: ${currentThreadId()}")
        val mainThread = KThread.currentThread()

        thread {
            println("Started Worker going to sleep: ${currentThreadId()}")
            sleep(1000)
            println("Unparking from: ${currentThreadId()}")
            Parker.unpark(mainThread)
            println("Unparked from: ${currentThreadId()}")
        }


        println("Parking from: ${currentThreadId()}")
        Parker.park()
        println("Unparked at: ${currentThreadId()}")


        assertTrue(true)
    }

    @Test
    fun unparkPark() {
        println("Started Main: ${currentThreadId()}")
        val mainThread = KThread.currentThread()

        thread {
            currentThreadId()
            println("Unparking from: ${currentThreadId()}")
            Parker.unpark(mainThread)
            println("Unparked from: ${currentThreadId()}")
        }

        println("Main going to sleep before park: ${currentThreadId()}")
        sleep(1000)

        println("Parking thread: ${currentThreadId()}")
        Parker.park() 
        println("Continued thread: ${currentThreadId()}")


        assertTrue(true)
    }
}
