package kotlinx.atomicfu.parking

import kotlinx.cinterop.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import platform.posix.*

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual object ParkingDelegator {
    actual fun createRef(): ParkingData {
        val mut = nativeHeap.alloc<pthread_mutex_t>().ptr
        val cond = nativeHeap.alloc<pthread_cond_t>().ptr
        callAndVerifyNative(0) { pthread_mutex_init(mut, null) }
        callAndVerifyNative(0) { pthread_cond_init(cond, null) }
        return ParkingData(mut, cond)
    }

    actual fun wait(ref: ParkingData, shouldWait: () -> Boolean){
        callAndVerifyNative(0) { pthread_mutex_lock(ref.mut) }
        if (shouldWait()) callAndVerifyNative(0) { pthread_cond_wait(ref.cond, ref.mut) }
        callAndVerifyNative(0) { pthread_mutex_unlock(ref.mut) }
    }
    
    actual fun timedWait(ref: ParkingData, nanos: Long, shouldWait: () -> Boolean): Unit = memScoped {
        val ts = alloc<timespec>().ptr

        // Add nanos to current time
        clock_gettime(CLOCK_REALTIME.toInt(), ts)
        ts.pointed.tv_sec = (ts.pointed.tv_sec.convert<Long>() + nanos / 1_000_000_000).convert()
        ts.pointed.tv_nsec = (ts.pointed.tv_nsec.convert<Long>() + nanos % 1_000_000_000).convert()
        //Fix overflow
        if (ts.pointed.tv_nsec >= 1_000_000_000) {
            ts.pointed.tv_sec += 1
            ts.pointed.tv_nsec -= 1_000_000_000
        }
        callAndVerifyNative(0) { pthread_mutex_lock(ref.mut) }
        if (shouldWait()) callAndVerifyNative(0, ETIMEDOUT) { pthread_cond_timedwait(ref.cond, ref.mut, ts) }
        callAndVerifyNative(0) { pthread_mutex_unlock(ref.mut) }
    }

    actual fun wake(ref: ParkingData) {
        callAndVerifyNative(0) { pthread_mutex_lock(ref.mut) }
        callAndVerifyNative(0) { pthread_cond_signal(ref.cond) }
        callAndVerifyNative(0) { pthread_mutex_unlock(ref.mut) }
    }

    actual fun destroyRef(ref: ParkingData) {
        callAndVerifyNative(0) { pthread_mutex_destroy(ref.mut) }
        callAndVerifyNative(0) { pthread_cond_destroy(ref.cond) }
        nativeHeap.free(ref.mut)
        nativeHeap.free(ref.cond)
    }

}
internal actual class ParkingData(val mut: CPointer<pthread_mutex_t>, val cond: CPointer<pthread_cond_t>)
