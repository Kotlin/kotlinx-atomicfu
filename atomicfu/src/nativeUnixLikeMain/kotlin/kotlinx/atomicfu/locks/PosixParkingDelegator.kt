package kotlinx.atomicfu.locks

import kotlinx.cinterop.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import platform.posix.*

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual object ParkingDelegator {
    actual fun createRef(): ParkingData {
        val mut = nativeHeap.alloc<pthread_mutex_t>().ptr
        val cond = nativeHeap.alloc<pthread_cond_t>().ptr
        val attr = nativeHeap.alloc<pthread_condattr_t>().ptr
        callAndVerify { pthread_mutex_init(mut, null) }
        callAndVerify { pthread_condattr_init(attr) }
        callAndVerify { pthreadCondAttrSetClock(attr) }
        callAndVerify { pthread_cond_init(cond, attr) }

        callAndVerify { pthread_condattr_destroy(attr) }
        nativeHeap.free(attr)
        return ParkingData(mut, cond)
    }

    actual inline fun wait(ref: ParkingData, shouldWait: () -> Boolean) {
        callAndVerify { pthread_mutex_lock(ref.mut) }
        try {
            if (shouldWait()) callAndVerify { pthread_cond_wait(ref.cond, ref.mut) }
        } finally {
            callAndVerify { pthread_mutex_unlock(ref.mut) }
        }
    }
    
    actual inline fun timedWait(ref: ParkingData, nanos: Long, shouldWait: () -> Boolean): Unit = memScoped {
        val ts = alloc<timespec>().ptr

        // Add nanos to current time
        callAndVerify { clock_gettime(posixGetTimeClockId.convert(), ts) }
        ts.pointed.tv_sec = ts.pointed.tv_sec.addNanosToSeconds(nanos)
        ts.pointed.tv_nsec = (ts.pointed.tv_nsec + nanos % 1_000_000_000).convert()
        //Fix overflow
        if (ts.pointed.tv_nsec >= 1_000_000_000) {
            ts.pointed.tv_sec = ts.pointed.tv_sec.addNanosToSeconds(1_000_000_000)
            ts.pointed.tv_nsec -= 1_000_000_000
        }
        callAndVerify { pthread_mutex_lock(ref.mut) }
        try {
            if (shouldWait()) callAndVerify(0, ETIMEDOUT) { pthread_cond_timedwait(ref.cond, ref.mut, ts) }
        } finally {
            callAndVerify { pthread_mutex_unlock(ref.mut) }
        }
    }

    actual fun wake(ref: ParkingData) {
        callAndVerify { pthread_mutex_lock(ref.mut) }
        try {
            callAndVerify { pthread_cond_signal(ref.cond) }
        } finally {
            callAndVerify { pthread_mutex_unlock(ref.mut) }
        }
    }

    actual fun destroyRef(ref: ParkingData) {
        callAndVerify { pthread_mutex_destroy(ref.mut) }
        callAndVerify { pthread_cond_destroy(ref.cond) }
        nativeHeap.free(ref.mut)
        nativeHeap.free(ref.cond)
    }
}

internal actual class ParkingData(val mut: CPointer<pthread_mutex_t>, val cond: CPointer<pthread_cond_t>)

internal expect val posixGetTimeClockId: Int
internal expect fun pthreadCondAttrSetClock(attr: CPointer<pthread_condattr_t>): Int