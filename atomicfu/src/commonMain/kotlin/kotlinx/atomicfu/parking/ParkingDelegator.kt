package kotlinx.atomicfu.parking

/**
 * Internal utility that delegates the thread suspending and resuming calls calls in the platform specific way (darwin, linux, windows).
 * On jvm delegates to LockSupport.Park.
 */
internal interface ParkingDelegator {
    fun createRef(): Any
    fun wait(ref: Any) 
    fun timedWait(ref: Any, nanos: Long)
    fun wake(ref: Any)
    fun destroyRef(ref: Any)
}