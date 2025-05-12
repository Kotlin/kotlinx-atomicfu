package kotlinx.atomicfu.locks

import kotlinx.atomicfu.locks.ParkingSupport.currentThreadHandle
import kotlinx.atomicfu.locks.ParkingSupport.park
import kotlinx.atomicfu.locks.ParkingSupport.unpark
import kotlin.time.Duration
import kotlin.time.TimeMark

/**
 * Parking and unparking support for threads on Kotlin/Native and Kotlin/JVM.
 * Can be used as a building block to create locks and other synchronization primitives.
 *
 * A call to [ParkingSupport.park] or [ParkingSupport.parkUntil] will pause the current thread.
 * A paused thread will resume in one of the following four cases:
 * - A different thread calls [ParkingSupport.unpark].
 * - The given `timeout` or `deadline` is exceeded.
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
 *
 * PLEASE NOTE: this is a low-level API and should be used with caution.
 * Unless the goal is to create a _synchronization primitive_ like a mutex or semaphore,
 * it is advised to use a higher level concurrency API like `kotlinx.coroutines`
 */
@ExperimentalThreadBlockingApi
expect object ParkingSupport {

    /**
     * Parks the current thread for [timeout] duration.
     *
     * Wakes up in the following cases:
     * - A different thread calls [ParkingSupport.unpark].
     * - The [timeout] is exceeded.
     * - A spurious wakeup
     * - (Only on JVM) The thread was interrupted. The interrupted flag stays set after wakeup.
     * A future call to [park] this thread will return immediately, unless the `Thread.interrupted` flag is cleared.
     */
    fun park(timeout: Duration)

    /**
     * Parks the current thread until [deadline] is reached.
     *
     * Wakes up in the following cases:
     * - A different thread calls [ParkingSupport.unpark].
     * - The given [deadline] has passed.
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
     * Returns the [ParkingHandle] corresponding to the current thread.
     * This [ParkingHandle] should be shared with other threads which allow them to [unpark] the current thread.
     *
     * A [ParkingHandle] is uniquely associated with a specific thread, maintaining a one-to-one correspondence.
     * When the _same_ thread makes multiple calls to [currentThreadHandle],
     * it always returns the _same_ [ParkingHandle].
     *
     * Note: as this function returns a unique [ParkingHandle] for each thread it should not be cached or memoized.
     */
    fun currentThreadHandle(): ParkingHandle
}

/**
 * A handle allowing to unpark a thread of execution using [ParkingSupport.unpark].
 * There is a one-to-one mapping between threads and parking handles.
 * A handle can be obtained by calling [ParkingSupport.currentThreadHandle].
 * Refer to [ParkingSupport] documentation for more details
 * on how to use [ParkingHandle] and how parking works in general.
 */
@ExperimentalThreadBlockingApi
expect class ParkingHandle

/**
 * Marks [ParkingHandle] and [ParkingSupport] API as experimental.
 * 
 * The APIs and semantics can change in the future and are considered to be low-level.
 * Unless the goal is to create a _synchronization primitive_ like a mutex or semaphore,
 * it is advised to use a higher level concurrency API like `kotlinx.coroutines`
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is experimental. It is low-level and might change in the future."
)
public annotation class ExperimentalThreadBlockingApi
