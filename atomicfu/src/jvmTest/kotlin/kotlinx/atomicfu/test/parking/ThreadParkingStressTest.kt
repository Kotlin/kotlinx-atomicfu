package kotlinx.atomicfu.test.parking

import kotlinx.atomicfu.parking.KThread
import kotlinx.atomicfu.parking.Parker
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.measureTime
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ThreadParkingStressTest {

    @Test
    fun parkingStressJvm() {
        val duration = measureTime {
            val kthread = AtomicReference<KThread?>(null)
            val done = AtomicBoolean(false)

            val thread1 = thread {
                kthread.set(KThread.currentThread())
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleep(Random.nextLong(0, 5))
                        Parker.park()
                    } else {
                        Parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                done.set(true)
            }
            
            val thread2 = thread {
                while (!done.get()) {
                    sleep(Random.nextLong(0, 5))
                    kthread.get()?.let { Parker.unpark(it) }
                }
            }
            
            val thread3 = thread {
                while (!done.get()) {
                    sleep(Random.nextLong(0, 5))
                    kthread.get()?.let { Parker.unpark(it) }
                }
            }

            thread1.join()
            thread2.join()
            thread3.join()
        }
        println(duration)
    }

    @Test
    fun testPublicApiNative() {
        val duration = measureTime {
            val kthread0 = AtomicReference<KThread?>(null)
            val done0 = AtomicBoolean(false)

            val kthread1 = AtomicReference<KThread?>(null)
            val done1 = AtomicBoolean(false)

            val thread0 = thread {
                kthread0.set(KThread.currentThread())
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleep(Random.nextLong(0, 5))
                        Parker.park()
                    } else {
                        Parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                done0.set(true)
            }

            val thread1 = thread {
                kthread1.set(KThread.currentThread())
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleep(Random.nextLong(0, 5))
                        Parker.park()
                    } else {
                        Parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                done1.set(true)
            }

            val thread2 = thread {
                while (!done0.get() || !done1.get()) {
                    sleep(Random.nextLong(0, 5))
                    if (Random.nextBoolean()) {
                        kthread0.get()?.let {Parker.unpark(it)}
                    } else {
                        kthread1.get()?.let {Parker.unpark(it)}
                    }
                }
            }

            val thread3 = thread {
                while (!done0.get() || !done1.get()) {
                    sleep(Random.nextLong(0, 5))
                    if (Random.nextBoolean()) {
                        kthread0.get()?.let {Parker.unpark(it)}
                    } else {
                        kthread1.get()?.let {Parker.unpark(it)}
                    }
                }
            }

            thread0.join()
            thread1.join()
            thread2.join()
            thread3.join()
        }
        println(duration)
    }
}
