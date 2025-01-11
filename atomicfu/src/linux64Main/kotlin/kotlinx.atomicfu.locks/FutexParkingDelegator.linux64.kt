package kotlinx.atomicfu.locks

import kotlinx.cinterop.*
import platform.linux.SYS_futex
import platform.posix.*

const val FUTEX_WAIT = 0
const val FUTEX_WAKE = 1

@OptIn(ExperimentalForeignApi::class)
internal actual object FutexParkingDelegator: ParkingDelegator {
    actual override fun createFutexPtr(): Long {
        val signal = nativeHeap.alloc<UIntVar>()
        signal.value = 0u
        return signal.ptr.toLong()
    }

    actual override fun wait(futexPrt: Long): Boolean {
        val cPtr = futexPrt.toCPointer<UIntVar>() ?: throw IllegalStateException("Could not create C Pointer from futex ref")
        val result = syscall(SYS_futex.toLong(), futexPrt, FUTEX_WAIT, 0u, NULL)
        val interrupted = result.toInt() == EINTR
        nativeHeap.free(cPtr)
        return interrupted
    }

    actual override fun wake(futexPrt: Long): Int {
        //Returns n threads woken up (needs to be 1)
        val result = syscall(SYS_futex.toLong(), futexPrt, FUTEX_WAKE, 1u, NULL).toInt()
        return if (result == 1) 0 else -1
    }

    actual override fun manualDeallocate(futexPrt: Long) {
        val cPtr = futexPrt.toCPointer<UIntVar>() ?: throw IllegalStateException("Could not create C Pointer from futex ref")
        nativeHeap.free(cPtr)
    }
}
