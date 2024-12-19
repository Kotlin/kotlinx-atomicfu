package kotlinx.atomicfu.parking

import platform.posix.sleep
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ObsoleteWorkersApi::class)
class ThreadParkerTest {

    @Test
    fun parkUnpark() {
        currentThreadId()
        println("Started Main: ${currentThreadId()}")

        val p = ThreadParker(PosixParkingDelegator)

        val worker = Worker.start()
        worker.execute(TransferMode.UNSAFE, { p }) { p ->
            currentThreadId()
            println("Started Worker going to sleep: ${currentThreadId()}")
            sleep(1u)
            println("Unparking from: ${currentThreadId()}")
            p.unpark()
            println("Unparked from: ${currentThreadId()}")
        }

        println("Parking from: ${currentThreadId()}")
        p.park()
        println("Unparked at: ${currentThreadId()}")


        assertTrue(true)
    }

    @Test
    fun unparkPark() {
        currentThreadId()
        println("Started Main: ${currentThreadId()}")

        val p = ThreadParker(PosixParkingDelegator)

        val worker = Worker.start()
        worker.execute(TransferMode.UNSAFE, { p }) { p ->
            currentThreadId()
            println("Unparking from: ${currentThreadId()}")
            p.unpark()
            println("Unparked from: ${currentThreadId()}")
        }
        
        println("Main going to sleep before park: ${currentThreadId()}")
        sleep(5u)

        println("Parking thread: ${currentThreadId()}")
        p.park()
        println("Continued thread: ${currentThreadId()}")


        assertTrue(true)
    }
}

