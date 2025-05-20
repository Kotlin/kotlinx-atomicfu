package kotlinx.atomicfu.locks

import java.util.concurrent.locks.LockSupport

internal actual object ParkingDelegator {

    actual fun createRef(): ParkingData = Thread.currentThread()
    actual fun wait(ref: ParkingData, shouldWait: () -> Boolean) = LockSupport.park()
    actual fun timedWait(ref: ParkingData, nanos: Long, shouldWait: () -> Boolean) = LockSupport.parkNanos(nanos)
    actual fun wake(ref: ParkingData) = LockSupport.unpark(ref)
    actual fun destroyRef(ref: ParkingData) {}

}

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias ParkingData = Thread