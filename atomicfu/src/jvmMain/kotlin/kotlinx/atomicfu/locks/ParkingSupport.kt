package kotlinx.atomicfu.locks

import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeMark

actual typealias ParkingHandle = Thread
actual object ParkingSupport {
    actual fun park(timeout: Duration) {
        if (timeout == Duration.INFINITE) LockSupport.park()
        else LockSupport.parkNanos(timeout.toLong(DurationUnit.NANOSECONDS))
    }
    actual fun parkUntil(deadline: TimeMark) = park(deadline.elapsedNow() * -1)
    actual fun unpark(handle: ParkingHandle) = LockSupport.unpark(handle)
    actual fun currentThreadHandle(): ParkingHandle = Thread.currentThread()
}