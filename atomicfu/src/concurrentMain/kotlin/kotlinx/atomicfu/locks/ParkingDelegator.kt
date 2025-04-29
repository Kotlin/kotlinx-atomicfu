package kotlinx.atomicfu.locks

/**
 * Object that stores references that need to be manually destroyed and deallocated, 
 * after native pthread_cond_wait usage.
 */
internal expect class ParkingData

/**
 * Internal utility that delegates the thread suspending and resuming to pthread_cond_wait on native.
 * On jvm delegates to LockSupport.Park.
 */
internal expect object ParkingDelegator {
    fun createRef(): ParkingData
    fun wait(ref: ParkingData, shouldWait: () -> Boolean) 
    fun timedWait(ref: ParkingData, nanos: Long, shouldWait: () -> Boolean)
    fun wake(ref: ParkingData)
    fun destroyRef(ref: ParkingData)
}

/**
 * Adds nano seconds to current time in seconds.
 * Clamps for Int.
 */
internal fun Int.addNanosToSeconds(nanos: Long): Int = 
    (this + nanos / 1_000_000_000).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

/**
 * Adds nano seconds to current time in seconds.
 */
internal fun Long.addNanosToSeconds(nanos: Long): Long {
    
    // Should never happen as this is checked in `ThreadParker`
    check(nanos >= 0) { "Cannot wait for a negative number of nanoseconds" }
    val result =  this + nanos / 1_000_000_000
    
    // Overflow check: should never happen since this is very far into the future.
    check(!(this xor result < 0 && this >= 0)) { "Nano seconds addition overflowed, current time in seconds is $this" }
    return result
}
