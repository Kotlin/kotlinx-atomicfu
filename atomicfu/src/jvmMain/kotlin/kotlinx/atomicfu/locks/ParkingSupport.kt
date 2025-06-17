package kotlinx.atomicfu.locks

import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeMark

@ExperimentalThreadBlockingApi
public actual typealias ParkingHandle = Thread

@ExperimentalThreadBlockingApi
public actual object ParkingSupport {
    public actual fun park(timeout: Duration) {
        if (timeout == Duration.INFINITE) LockSupport.park()
        else LockSupport.parkNanos(timeout.toLong(DurationUnit.NANOSECONDS))
    }

    public actual fun parkUntil(deadline: TimeMark): Unit = park(deadline.elapsedNow() * -1)
    public actual fun unpark(handle: ParkingHandle): Unit = LockSupport.unpark(handle)
    public actual fun currentThreadHandle(): ParkingHandle = Thread.currentThread()
}
