package kotlinx.atomicfu.locks
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

// Only for testing purposes
internal class JvmParkingDelegator: ParkingDelegator {
    private var thread: Thread? = null
    private val atomicLong: AtomicLong = AtomicLong(0L)

    override fun createFutexPtr(): Long {
        thread = Thread.currentThread()
        return 0L
    }

    override fun wait(futexPrt: Long): Boolean {
        while (atomicLong.get() == 0L) {
            LockSupport.park()
        }
        thread = null
        return false
    }

    override fun wake(futexPrt: Long): Int {
        if (atomicLong.compareAndSet(0L, 1L)) {
            LockSupport.unpark(thread)
        }
        return 0
    }

    override fun manualDeallocate(futexPrt: Long) {}
}
