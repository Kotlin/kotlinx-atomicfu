package kotlinx.atomicfu.parking

import platform.posix.sleep
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

@OptIn(ObsoleteWorkersApi::class)
class ThreadParkerTest {

    @Test
    fun parkUnpark() {

        val p = ThreadParker()

        val worker = Worker.start()
        worker.execute(TransferMode.UNSAFE, { p }) { p ->
            currentThreadId()
            sleep(1u)
            p.unpark()
        }

        val t = measureTime {
            p.park()
        }

        assertTrue(t.inWholeMilliseconds > 900)
    }

    @Test
    fun unparkPark() {
        val p = ThreadParker()

        val worker = Worker.start()
        worker.execute(TransferMode.UNSAFE, { p }) { p ->
            p.unpark()
        }
        
        sleep(5u)
        p.park()
        assertTrue(true)
    }
}

