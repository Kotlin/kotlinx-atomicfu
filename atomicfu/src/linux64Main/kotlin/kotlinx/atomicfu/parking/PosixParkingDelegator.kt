package kotlinx.atomicfu.parking

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.pthread_cond_destroy
import platform.posix.pthread_cond_init
import platform.posix.pthread_cond_signal
import platform.posix.pthread_cond_t
import platform.posix.pthread_cond_timedwait
import platform.posix.pthread_cond_wait
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.timespec

@OptIn(ExperimentalForeignApi::class)
internal actual object PosixParkingDelegator : ParkingDelegator {
    actual override fun createRef(): Any {
        val combo = ParkingData(nativeHeap.alloc<pthread_mutex_t>().ptr, nativeHeap.alloc<pthread_cond_t>().ptr)
        callAndVerifyNative(0)  { pthread_mutex_init(combo.mut, null) }
        callAndVerifyNative(0)  { pthread_cond_init(combo.cond, null) }
        return combo
    }

    actual override fun wait(ref: Any) {
        if (ref !is ParkingData) throw IllegalArgumentException("ParkingDelegator got incompatible parking object")
        callAndVerifyNative(0)  { pthread_mutex_lock(ref.mut) }
        while (!ref.wake.value) {
            callAndVerifyNative(0)  { pthread_cond_wait(ref.cond, ref.mut) }
        }
        callAndVerifyNative(0)  { pthread_mutex_unlock(ref.mut) }
    }

    actual override fun timedWait(ref: Any, nanos: Long) {
        if (ref !is ParkingData) throw IllegalArgumentException("ParkingDelegator got incompatible parking object")
        val ts = nativeHeap.alloc<timespec>().ptr
        
        // Add nanos to current time
        clock_gettime(CLOCK_REALTIME.toInt(), ts)
        ts.pointed.tv_sec += nanos / 1_000_000_000
        ts.pointed.tv_nsec += nanos % 1_000_000_000
        
        //Fix overflow
        if (ts.pointed.tv_nsec >= 1_000_000_000) {
            ts.pointed.tv_sec += 1
            ts.pointed.tv_nsec -= 1_000_000_000
        }
        var rc = 0
        callAndVerifyNative(0)  { pthread_mutex_lock(ref.mut) }
        while (!ref.wake.value && rc == 0) {
            rc = pthread_cond_timedwait(ref.cond, ref.mut, ts)
        }
        callAndVerifyNative(0)  { pthread_mutex_unlock(ref.mut) }
        nativeHeap.free(ts)
    }
    
    actual override fun wake(ref: Any) {
        if (ref !is ParkingData) throw IllegalArgumentException("ParkingDelegator got incompatible parking object")
        callAndVerifyNative(0)  { pthread_mutex_lock(ref.mut) }
        ref.wake.value = true
        callAndVerifyNative(0)  { pthread_cond_signal(ref.cond) }
        callAndVerifyNative(0)  { pthread_mutex_unlock(ref.mut) }
    }

    actual override fun destroyRef(ref: Any) {
        if (ref !is ParkingData) throw IllegalArgumentException("ParkingDelegator got incompatible parking object")
        callAndVerifyNative(0)  { pthread_mutex_destroy(ref.mut) }
        callAndVerifyNative(0)  { pthread_cond_destroy(ref.cond) }
        nativeHeap.free(ref.mut)
        nativeHeap.free(ref.cond)
    }

    internal data class ParkingData(val mut: CPointer<pthread_mutex_t>, val cond: CPointer<pthread_cond_t>) {
        val wake: AtomicBoolean = atomic(false)
    }
}