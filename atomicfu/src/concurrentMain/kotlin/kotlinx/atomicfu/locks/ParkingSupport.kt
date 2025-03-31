package kotlinx.atomicfu.locks

import kotlin.time.Duration
import kotlin.time.TimeMark

/**
 * Parking and unparking support for threads on Kotlin/Native and Kotlin/JVM. 
 * Can be used as a building block to create locks and other synchronization primitives.
 *
 * A call to [ParkingSupport.park] or [ParkingSupport.parkUntil] will suspend the current thread.
 * A suspended thread will wake up in one of the following four cases:
 * - A call to [ParkingSupport.unpark] 
 * - The given `timout` or `deadline` is exceeded
 * - A spurious wakeup
 * - (Only on JVM) The thread was interrupted
 * 
 * The caller is responsible for verifying the reason of wakeup and how to respond accordingly.
 * 
 * Example usage parking thread:
 * ```Kotlin
 * // publish my parking handle
 * handleReference.value = ParkingSupport.currentThreadHandle()
 * // wait
 * while (state.value == WAIT) {
 *   ParkingSupport.park(Duration.INFINITE)
 * }
 * ```
 * 
 * Example usage unparker thread:
 * ```Kotlin
 * state.value = WAKE
 * ParkingSupport.unpark(handleReference.value)
 * ```
 */
expect object ParkingSupport {
    
    /**
     * Parks the current thread for [timout] duration.
     * 
     * Wakes up in the following cases:
     * - A call to [ParkingSupport.unpark]
     * - [timout] is exceeded
     * - A spurious wakeup
     * - (Only on JVM) The thread was interrupted 
     */
    fun park(timout: Duration)
    
    /**
     * Parks the current thread until [deadline] is reached.
     *
     * Wakes up in the following cases:
     * - A call to [ParkingSupport.unpark]
     * - [deadline] is exceeded
     * - A spurious wakeup
     * - (Only on JVM) The thread was interrupted
     */
    fun parkUntil(deadline: TimeMark)

    /**
     * Unparks the thread corresponding to [handle].
     * If the corresponding thread was not parked the next park call will return immediately.
     * A [ParkingHandle] can only be pre-unparked once.
     */
    fun unpark(handle: ParkingHandle)

    /**
     * Returns the [ParkingHandle] that can be used to unpark the current thread.
     */
    fun currentThreadHandle(): ParkingHandle
}

/**
 * Is used to unpark the current thread.
 * Can be obtained by calling [ParkingSupport.currentThreadHandle].
 * Is required by [ParkingSupport.unpark].
 */
expect class ParkingHandle