package kotlinx.atomicfu.parking
import kotlinx.atomicfu.atomic
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import platform.posix.*
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.measureTime

class ThreadParkingStressTest {
    
    @Test
    fun parkingStressNative() {
        val duration = measureTime {
            val parker = ThreadParker(PosixParkingDelegator)
            val pt = ParkTest(parker)

            val worker1 = Worker.start()
            val future1 = worker1.execute(TransferMode.UNSAFE, { pt }) { pt ->
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        usleep(Random.nextUInt(0u, 500u))
                        pt.parker.park()
                    } else {
                        pt.parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                pt.done.value = true
            }
            val worker2 = Worker.start()
            val future2 = worker2.execute(TransferMode.UNSAFE, { pt }) { pt ->
                while (!pt.done.value) {
                    usleep(Random.nextUInt(0u, 500u))
                    pt.parker.unpark()
                }
            }
            val worker3 = Worker.start()
            val future3 = worker3.execute(TransferMode.UNSAFE, { pt }) { pt ->
                while (!pt.done.value) {
                    usleep(Random.nextUInt(0u, 500u))
                    pt.parker.unpark()
                }
            }

            future1.result
            future2.result
            future3.result
        }
        println(duration)
    }
    
    internal class ParkTest(val parker: ThreadParker) {
        val done = atomic(false)
    }
    
    internal class PublicParkerTest {
        val thread0 = atomic<KThread?>(null)
        val thread1 = atomic<KThread?>(null)
        val done0 = atomic(false)
        val done1 = atomic(false)
    }

    @Test
    fun testPublicApiNative() {
        val duration = measureTime {
            val ppt = PublicParkerTest()

            val worker0 = Worker.start()
            val future0 = worker0.execute(TransferMode.UNSAFE, { ppt }) { ppt ->
                val thread = KThread.currentThread()
                ppt.thread0.value = thread
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        usleep(Random.nextUInt(0u, 500u))
                        Parker.park()
                    } else {
                        Parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                ppt.done0.value = true
            }
            val worker1 = Worker.start()
            val future1 = worker1.execute(TransferMode.UNSAFE, { ppt }) { ppt ->
                val thread = KThread.currentThread()
                ppt.thread1.value = thread
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        usleep(Random.nextUInt(0u, 500u))
                        Parker.park()
                    } else {
                        Parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                ppt.done1.value = true
            }
            val worker2 = Worker.start()
            val future2 = worker2.execute(TransferMode.UNSAFE, { ppt }) { ppt ->
                while (!ppt.done0.value || !ppt.done1.value) {
                    usleep(Random.nextUInt(0u, 500u))
                    if (Random.nextBoolean()) {
                        ppt.thread0.value?.let {Parker.unpark(it)}
                    } else {
                        ppt.thread1.value?.let {Parker.unpark(it)}
                    }
                }
            }
            val worker3 = Worker.start()
            val future3 = worker3.execute(TransferMode.UNSAFE, { ppt }) { ppt ->
                while (!ppt.done0.value || !ppt.done1.value) {
                    usleep(Random.nextUInt(0u, 500u))
                    if (Random.nextBoolean()) {
                        ppt.thread0.value?.let {Parker.unpark(it)}
                    } else {
                        ppt.thread1.value?.let {Parker.unpark(it)}
                    }
                }
            }
            
            future0.result
            future1.result
            future2.result
            future3.result
        }
        println(duration)
    }
    
}