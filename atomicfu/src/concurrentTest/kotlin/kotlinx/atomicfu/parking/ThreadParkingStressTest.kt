package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.measureTime

class ThreadParkingStressTest {
    private class Atomics {
        val kthread = atomic<KThread?>(null)
        val done = atomic(false)
    }
    @Test
    fun parkingStress() {
        val duration = measureTime {
            val a = Atomics()

            val thread1 = testThread {
                a.kthread.value = KThread.currentThread()
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleepMills(Random.nextLong(0, 5))
                        Parker.park()
                    } else {
                        Parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                a.done.value = true
            }

            val thread2 = testThread {
                while (a.done.value) {
                    sleepMills(Random.nextLong(0, 5))
                    a.kthread.value?.let { Parker.unpark(it) }
                }
            }

            val thread3 = testThread {
                while (!a.done.value) {
                    sleepMills(Random.nextLong(0, 5))
                    a.kthread.value?.let { Parker.unpark(it) }
                }
            }

            thread1.join()
            thread2.join()
            thread3.join()
        }
        println(duration)
    }

    @Test
    fun testPublicApi() {
        val duration = measureTime {
            val a0 = Atomics()
            val a1 = Atomics()

            val thread0 = testThread {
                a0.kthread.value = KThread.currentThread()
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleepMills(Random.nextLong(0, 5))
                        Parker.park()
                    } else {
                        Parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                a0.done.value = true
            }

            val thread1 = testThread {
                a1.kthread.value = KThread.currentThread()
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleepMills(Random.nextLong(0, 5))
                        Parker.park()
                    } else {
                        Parker.parkNanos(Random.nextLong(0, 500))
                    }
                }
                a1.done.value = true
            }

            val thread2 = testThread {
                while (!a0.done.value || !a1.done.value) {
                    sleepMills(Random.nextLong(0, 5))
                    if (Random.nextBoolean()) {
                        a0.kthread.value?.let { Parker.unpark(it)}
                    } else {
                        a1.kthread.value?.let { Parker.unpark(it)}
                    }
                }
            }

            val thread3 = testThread {
                while (!a0.done.value || !a1.done.value) {
                    sleepMills(Random.nextLong(0, 5))
                    if (Random.nextBoolean()) {
                        a0.kthread.value?.let { Parker.unpark(it)}
                    } else {
                        a1.kthread.value?.let { Parker.unpark(it)}
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
