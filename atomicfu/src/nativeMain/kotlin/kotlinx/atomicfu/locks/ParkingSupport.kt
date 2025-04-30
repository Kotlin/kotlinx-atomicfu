package kotlinx.atomicfu.locks

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeMark

@kotlin.native.concurrent.ThreadLocal
private val threadLocalParkingHandle = ParkingHandle()

actual class ParkingHandle internal constructor() {
    internal val parker: ThreadParker = ThreadParker()
}

actual object ParkingSupport {
    actual fun park(timeout: Duration) {
        if (timeout == Duration.INFINITE) threadLocalParkingHandle.parker.park()
        else threadLocalParkingHandle.parker.parkNanos(timeout.toLong(DurationUnit.NANOSECONDS))
    }

    actual fun parkUntil(deadline: TimeMark) = park(deadline.elapsedNow() * -1)
    actual fun unpark(handle: ParkingHandle) = handle.parker.unpark()
    actual fun currentThreadHandle(): ParkingHandle = threadLocalParkingHandle
}
