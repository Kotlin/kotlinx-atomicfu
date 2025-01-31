package kotlinx.atomicfu.parking

/**
 * Internal utility that delegates the thread suspending and resuming calls calls in the platform specific way (darwin, linux, windows).
 * On jvm delegates to LockSupport.Park.
 */

internal expect class ParkingData

internal expect object ParkingDelegator {
    fun createRef(): ParkingData
    fun wait(ref: ParkingData) 
    fun timedWait(ref: ParkingData, nanos: Long)
    fun wake(ref: ParkingData)
    fun destroyRef(ref: ParkingData)
}