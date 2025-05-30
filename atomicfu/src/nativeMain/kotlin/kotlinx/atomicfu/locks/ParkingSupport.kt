package kotlinx.atomicfu.locks

import kotlin.native.concurrent.ThreadLocal
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeMark

@ThreadLocal
private val threadLocalParkingHandle = ParkingHandle()

@ExperimentalThreadBlockingApi
public actual class ParkingHandle internal constructor() {
    internal val parker: ThreadParker = ThreadParker()
}

@ExperimentalThreadBlockingApi
public actual object ParkingSupport {
    public actual fun park(timeout: Duration) {
        if (timeout == Duration.INFINITE) threadLocalParkingHandle.parker.park()
        else threadLocalParkingHandle.parker.parkNanos(timeout.toLong(DurationUnit.NANOSECONDS))
    }

    public actual fun parkUntil(deadline: TimeMark): Unit = park(deadline.elapsedNow() * -1)
    public actual fun unpark(handle: ParkingHandle): Unit = handle.parker.unpark()
    public actual fun currentThreadHandle(): ParkingHandle = threadLocalParkingHandle
}
