package kotlinx.atomicfu.locks

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal object PosixParkingDelegator: ParkingDelegator {
    override fun createFutexPtr(): Long {
        val combo = posixParkInit()
        return combo.toLong()
    }

    override fun wait(futexPrt: Long): Boolean {
        val comboPtr = futexPrt.toCPointer<posix_combo_t>() ?: throw IllegalStateException("Could not create C Pointer from pthread_cond ref")
        posixWait(comboPtr)
        return false
    }

    override fun wake(futexPrt: Long): Int {
        val comboPtr = futexPrt.toCPointer<posix_combo_t>() ?: throw IllegalStateException("Could not create C Pointer from pthread_cond ref")
        posixWake(comboPtr)
        return 0
    }

    override fun manualDeallocate(futexPrt: Long) {
        val comboPtr = futexPrt.toCPointer<posix_combo_t>() ?: throw IllegalStateException("Could not create C Pointer from pthread_cond ref")
        posixDestroy(comboPtr)
    }
}
