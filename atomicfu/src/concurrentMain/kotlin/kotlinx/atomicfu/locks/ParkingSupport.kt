package kotlinx.atomicfu.locks

import kotlin.time.Duration
import kotlin.time.TimeMark

/**
 * Parking and unparking support for threads on Kotlin/Native and Kotlin/JVM. 
 * Can be used as a building block to create locks and other synchronization primitives.
 *
 * A call to [ParkingSupport.park] or [ParkingSupport.parkUntil] will suspend the current thread.
 * A suspended thread will wake up in one of the following four cases:
 * - A different thread calls [ParkingSupport.unpark].
 * - The given `timout` or `deadline` is exceeded.
 * - A spurious wakeup
 * - (Only on JVM) The thread was interrupted. The interrupted flag stays set after wakeup.
 * A future call to [park] this thread will return immediately, unless the `Thread.interrupted` flag is cleared.
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
     * - A different thread calls [ParkingSupport.unpark].
     * - The given `timout` or `deadline` is exceeded.
     * - A spurious wakeup
     * - (Only on JVM) The thread was interrupted. The interrupted flag stays set after wakeup.
     * A future call to [park] this thread will return immediately, unless the `Thread.interrupted` flag is cleared.
     */
    fun park(timout: Duration)
    
    /**
     * Parks the current thread until [deadline] is reached.
     *
     * Wakes up in the following cases:
     * - A different thread calls [ParkingSupport.unpark].
     * - The given `timout` or `deadline` is exceeded.
     * - A spurious wakeup
     * - (Only on JVM) The thread was interrupted. The interrupted flag stays set after wakeup.
     * A future call to [park] this thread will return immediately, unless the `Thread.interrupted` flag is cleared.
     */
    fun parkUntil(deadline: TimeMark)

    /**
     * Unparks the thread corresponding to [handle].
     * If [unpark] is called while the corresponding thread is not parked, the next [park] call will return immediately
     * — the [ParkingHandle] is unparked ahead of time.
     * 
     * A [ParkingHandle] can only _remember_ one pre-unpark attempt at a time. 
     * Meaning, when two consecutive [unpark] calls are made while the corresponding thread is not parked, 
     * only the next park call will return immediately — [unpark] calls are not accumulated.
     */
    fun unpark(handle: ParkingHandle)

    /**
     * Returns the [ParkingHandle] that can be used to [unpark] the current thread.
     */
    fun currentThreadHandle(): ParkingHandle
}

/**
 * Is used to unpark the current thread.
 * Can be obtained by calling [ParkingSupport.currentThreadHandle].
 * Is required by [ParkingSupport.unpark].
 */
expect class ParkingHandle