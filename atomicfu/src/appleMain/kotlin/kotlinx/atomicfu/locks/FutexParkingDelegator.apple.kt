package kotlinx.atomicfu.locks

import kotlinx.cinterop.*
import platform.darwin.UInt32
import platform.darwin.UInt64Var
import platform.darwin.ulock.__ulock_wait
import platform.darwin.ulock.__ulock_wake

@OptIn(ExperimentalForeignApi::class)
internal actual object FutexParkingDelegator: ParkingDelegator {
    actual override fun createFutexPtr(): Long {
        val signal = nativeHeap.alloc<UInt64Var>()
        signal.value = 0u
        return signal.ptr.toLong()
    }

    actual override fun wait(futexPrt: Long): Boolean {
        val cPointer = futexPrt.toCPointer<UInt64Var>() ?: throw IllegalStateException("Could not create C Pointer from futex ref")
        val result = __ulock_wait(UL_COMPARE_AND_WAIT, cPointer, 0u, 0u)
        nativeHeap.free(cPointer)
        // THere is very little information about ulock so not sure what returned int stands for an interrupt
        // In any case it should be 0
        return result != 0
    }

    actual override fun wake(futexPrt: Long): Int {
        return __ulock_wake(UL_COMPARE_AND_WAIT, futexPrt.toCPointer<UInt64Var>(), 0u)
    }

    actual override fun manualDeallocate(futexPrt: Long) {
        val cPointer = futexPrt.toCPointer<UInt64Var>() ?: throw IllegalStateException("Could not create C Pointer from futex ref")
        nativeHeap.free(cPointer)
    }

    private const val UL_COMPARE_AND_WAIT: UInt32 = 1u
}
