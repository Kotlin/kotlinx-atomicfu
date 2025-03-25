package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.measureTime

class ThreadParkingStressTest {
    private class Atomics {
        val handle = atomic<ParkingHandle?>(null)
        val done = atomic(false)
    }
    @Test
    fun parkingStress() {
        val duration = measureTime {
            val a = Atomics()

            val thread1 = testThread {
                a.handle.value = ParkingSupport.currentThreadHandle()
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleepMills(Random.nextLong(0, 5))
                        ParkingSupport.park(Duration.INFINITE)
                    } else {
                        ParkingSupport.park(Random.nextLong(0, 500).nanoseconds)
                    }
                }
                a.done.value = true
            }

            val thread2 = testThread {
                while (a.done.value) {
                    sleepMills(Random.nextLong(0, 5))
                    a.handle.value?.let { ParkingSupport.unpark(it) }
                }
            }

            val thread3 = testThread {
                while (!a.done.value) {
                    sleepMills(Random.nextLong(0, 5))
                    a.handle.value?.let { ParkingSupport.unpark(it) }
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
                a0.handle.value = ParkingSupport.currentThreadHandle()
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleepMills(Random.nextLong(0, 5))
                        ParkingSupport.park(Duration.INFINITE)
                    } else {
                        ParkingSupport.park(Random.nextLong(0, 500).nanoseconds)
                    }
                }
                a0.done.value = true
            }

            val thread1 = testThread {
                a1.handle.value = ParkingSupport.currentThreadHandle()
                repeat(10000) { i ->
                    if (Random.nextBoolean()) {
                        sleepMills(Random.nextLong(0, 5))
                        ParkingSupport.park(Duration.INFINITE)
                    } else {
                        ParkingSupport.park(Random.nextLong(0, 500).nanoseconds)
                    }
                }
                a1.done.value = true
            }

            val thread2 = testThread {
                while (!a0.done.value || !a1.done.value) {
                    sleepMills(Random.nextLong(0, 5))
                    if (Random.nextBoolean()) {
                        a0.handle.value?.let { ParkingSupport.unpark(it) }
                    } else {
                        a1.handle.value?.let { ParkingSupport.unpark(it) }
                    }
                }
            }

            val thread3 = testThread {
                while (!a0.done.value || !a1.done.value) {
                    sleepMills(Random.nextLong(0, 5))
                    if (Random.nextBoolean()) {
                        a0.handle.value?.let { ParkingSupport.unpark(it) }
                    } else {
                        a1.handle.value?.let { ParkingSupport.unpark(it) }
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