package kotlinx.atomicfu.parking
import java.util.concurrent.locks.LockSupport

internal actual object ParkingDelegator {
    
    actual fun createRef(): ParkingData = ParkingData(Thread.currentThread())
    actual fun wait(ref: ParkingData, shouldWait: () -> Boolean) = LockSupport.park()
    actual fun timedWait(ref: ParkingData, nanos: Long, shouldWait: () -> Boolean) = LockSupport.parkNanos(nanos)
    actual fun wake(ref: ParkingData) = LockSupport.unpark(ref.thread)
    actual fun destroyRef(ref: ParkingData) {}
    
}

internal actual class ParkingData(val thread: Thread)