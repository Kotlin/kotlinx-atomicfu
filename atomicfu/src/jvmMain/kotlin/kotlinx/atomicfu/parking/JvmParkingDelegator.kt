package kotlinx.atomicfu.parking
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.time.DurationUnit
import kotlin.time.TimeSource.Monotonic

// Only for testing purposes
internal class JvmParkingDelegator: ParkingDelegator {
    private var thread: Thread? = null
    private val atomicLong: AtomicLong = AtomicLong(0L)
    
    override fun createRef(): Long {
        thread = Thread.currentThread()
        atomicLong.set(0L)
        return 0L
    }

    override fun wait(ref: Any) {
        while (atomicLong.get() == 0L) {
            LockSupport.park()
        }
    }
    
    override fun timedWait(ref: Any, nanos: Long) {
        val mark = Monotonic.markNow()
        while (atomicLong.get() == 0L) {
            LockSupport.parkNanos(nanos)
            if (mark.elapsedNow().toLong(DurationUnit.NANOSECONDS) > nanos) break
        }
    }


    override fun wake(ref: Any) {
        if (atomicLong.compareAndSet(0L, 1L)) {
            LockSupport.unpark(thread)
        }
    }


    override fun destroyRef(ref: Any) {
        thread = null
    }
}
