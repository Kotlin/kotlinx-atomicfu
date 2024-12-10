package kotlinx.atomicfu.locks

/**
 * Internal utility that delegates the futex or posix calls in the platform specific way (darwin, linux, windows).
 * On jvm delegates to LockSupport.Park. (The reason we need this on jvm is to verify the mutex with lincheck)
 */
internal interface ParkingDelegator {
    fun createFutexPtr(): Long
    fun wait(futexPrt: Long): Boolean
    fun wake(futexPrt: Long): Int
    fun manualDeallocate(futexPrt: Long)
}