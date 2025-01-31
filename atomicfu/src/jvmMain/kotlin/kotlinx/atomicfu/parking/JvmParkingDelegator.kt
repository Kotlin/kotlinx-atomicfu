package kotlinx.atomicfu.parking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlin.time.DurationUnit
import kotlin.time.TimeSource.Monotonic

internal actual object ParkingDelegator {
    
    actual fun createRef(): ParkingData {
        return ParkingData(Thread.currentThread())
    }

    actual fun wait(ref: ParkingData) {
        while (!ref.wake.get()) LockSupport.park()
    }
    
    actual fun timedWait(ref: ParkingData, nanos: Long) {
        val mark = Monotonic.markNow()
        while (!ref.wake.get()) {
            val remainingTime = nanos - mark.elapsedNow().toLong(DurationUnit.NANOSECONDS)
            if (remainingTime <= 0) break
            LockSupport.parkNanos(remainingTime)
        }
    }

    actual fun wake(ref: ParkingData) {
        if (ref.wake.compareAndSet(false, true)) {
            LockSupport.unpark(ref.thread)
        }
    }


    actual fun destroyRef(ref: ParkingData) {
    }
}

internal actual class ParkingData(val thread: Thread, val wake: AtomicBoolean = AtomicBoolean(false)) 