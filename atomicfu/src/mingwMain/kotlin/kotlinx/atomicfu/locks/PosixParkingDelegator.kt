package kotlinx.atomicfu.locks

import kotlinx.cinterop.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual object ParkingDelegator {
    actual fun createRef(): ParkingData {
        val mut = nativeHeap.alloc<pthread_mutex_tVar>().ptr
        val cond = nativeHeap.alloc<pthread_cond_tVar>().ptr
        callAndVerify(0)  { pthread_mutex_init(mut, null) }
        callAndVerify(0)  { pthread_cond_init(cond, null) }
        return ParkingData(mut, cond)
    }

    actual inline fun wait(ref: ParkingData, shouldWait: () -> Boolean) {
        callAndVerify(0)  { pthread_mutex_lock(ref.mut) }
        if (shouldWait()) callAndVerify(0)  { pthread_cond_wait(ref.cond, ref.mut) }
        callAndVerify(0)  { pthread_mutex_unlock(ref.mut) }
    }

    actual inline fun timedWait(ref: ParkingData, nanos: Long, shouldWait: () -> Boolean): Unit = memScoped {
        val ts = alloc<timespec>().ptr

        // Add nanos to current time
        callAndVerify(0) { clock_gettime(CLOCK_REALTIME, ts) }
        // According to https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-systemtime
        // the maximum year on windows is 30827.
        // Adding Long.MAX_VALUE / 1_000_000_000 should not be able to overflow.
        ts.pointed.tv_sec += nanos / 1_000_000_000
        ts.pointed.tv_nsec += (nanos % 1_000_000_000).toInt()

        //Fix overflow
        if (ts.pointed.tv_nsec >= 1_000_000_000) {
            ts.pointed.tv_sec += 1
            ts.pointed.tv_nsec -= 1_000_000_000
        }
        callAndVerify(0)  { pthread_mutex_lock(ref.mut) }
        if (shouldWait()) callAndVerify(0, ETIMEDOUT) { pthread_cond_timedwait(ref.cond, ref.mut, ts) }
        callAndVerify(0)  { pthread_mutex_unlock(ref.mut) }
    }

    actual fun wake(ref: ParkingData) {
        callAndVerify(0) { pthread_mutex_lock(ref.mut) }
        callAndVerify(0) { pthread_cond_signal(ref.cond) }
        callAndVerify(0) { pthread_mutex_unlock(ref.mut) }
    }

    actual fun destroyRef(ref: ParkingData) {
        callAndVerify(0) { pthread_mutex_destroy(ref.mut) }
        callAndVerify(0) { pthread_cond_destroy(ref.cond) }
        nativeHeap.free(ref.mut)
        nativeHeap.free(ref.cond)
    }
}
internal actual class ParkingData(val mut: CPointer<pthread_mutex_tVar>, val cond: CPointer<pthread_cond_tVar>)
