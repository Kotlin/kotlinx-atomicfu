package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.time.Duration

class ThreadParkerTest {
    val atomicHandle = atomic<ParkingHandle?>(null)
    val isPreUnparked = atomic(false)

    @Test
    fun parkUnpark() {
        var parkingHandle: ParkingHandle? = null

        val f = Fut {
            parkingHandle = ParkingSupport.currentThreadHandle()
            ParkingSupport.park(Duration.INFINITE)
        }

        // Allow thread to be parked before unpark call
        sleepMillis(500)
        ParkingSupport.unpark(parkingHandle!!)

        f.waitThrowing()
    }

    @Test
    fun unparkPark() {

        val f = Fut {
            atomicHandle.value = ParkingSupport.currentThreadHandle()

            while (!isPreUnparked.value) {
                sleepMillis(10)
            }

            ParkingSupport.park(Duration.INFINITE)
        }

        while (atomicHandle.value == null) {
            sleepMillis(10)
        }

        ParkingSupport.unpark(atomicHandle.value!!)
        isPreUnparked.value = true

        f.waitThrowing()
    }
}