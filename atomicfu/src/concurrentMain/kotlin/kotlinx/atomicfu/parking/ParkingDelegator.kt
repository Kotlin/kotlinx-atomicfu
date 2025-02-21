package kotlinx.atomicfu.parking

/**
 * Internal utility that delegates the thread suspending and resuming to pthread_cond_wait on native.
 * On jvm delegates to LockSupport.Park.
 */

internal expect class ParkingData

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
internal fun Long.addNanosToSeconds(nanos: Long): Long = this + nanos / 1_000_000_000
