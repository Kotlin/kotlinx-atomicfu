package kotlinx.atomicfu.parking

/**
 * Internal utility that delegates the futex or posix calls in the platform specific way (darwin, linux, windows).
 * On jvm delegates to LockSupport.Park. (The reason we need this on jvm is to verify the mutex with lincheck)
 */
internal interface ParkingDelegator {
    fun createRef(): Any
    fun wait(ref: Any) 
    fun timedWait(ref: Any, nanos: Long)
    fun wake(ref: Any)
    fun destroyRef(ref: Any)
}