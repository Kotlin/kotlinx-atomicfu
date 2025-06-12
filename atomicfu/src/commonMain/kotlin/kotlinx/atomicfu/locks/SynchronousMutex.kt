package kotlinx.atomicfu.locks

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

/**
 * Mutual exclusion for Kotlin Multiplatform.
 * 
 * It can protect a shared resource or critical section from multiple thread accesses.
 * Threads can acquire the lock by calling [lock] and release the lock by calling [unlock].
 * 
 * When a thread calls [lock] while another thread is locked, it will suspend until the lock is released.
 * When multiple threads are waiting for the lock, they will acquire it in a fair order (first in first out).
 * On JVM, a [lock] call can skip the queue if it happens in between a thread releasing and the first in queue acquiring.
 * 
 * It is reentrant, meaning the lock holding thread can call [lock] multiple times without suspending.
 * To release the lock (after multiple [lock] calls) an equal number of [unlock] calls are required.
 * 
 * This Mutex should not be used in combination with coroutines and `suspend` functions
 * as it blocks the waiting thread.
 * Use the `Mutex` from the coroutines library instead.
 * 
 * ```Kotlin
 * mutex.withLock {
 *     // Critical section only executed by
 *     // one thread at a time.
 * }
 * ```
 */
public expect class SynchronousMutex() {
    /**
     * Tries to lock this mutex, returning `false` if this mutex is already locked.
     *
     * It is recommended to use [withLock] for safety reasons, so that the acquired lock is always
     * released at the end of your critical section, and [unlock] is never invoked before a successful
     * lock acquisition.     
     * 
     * (JVM only) this call can potentially skip line.
     */
    public fun tryLock(): Boolean

    /**
     * Tries to lock this mutex within the given [timeout] period, 
     * returning `false` if the duration passed without locking.
     * 
     * Note: when [tryLock] succeeds the lock needs to be released by [unlock].
     * When [tryLock] does not succeed the lock does not have to be released.
     * 
     * (JVM only) throws Interrupted exception when thread is interrupted while waiting for lock.
     */
    public fun tryLock(timeout: Duration): Boolean

    /**
     * Locks the mutex, suspends the thread until the lock is acquired.
     * 
     * It is recommended to use [withLock] for safety reasons, so that the acquired lock is always
     * released at the end of your critical section, and [unlock] is never invoked before a successful
     * lock acquisition.
     */
    public fun lock()

    /**
     * Releases the lock.
     * Throws [IllegalStateException] when the current thread is not holding the lock.
     * 
     * It is recommended to use [withLock] for safety reasons, so that the acquired lock is always
     * released at the end of the critical section, and [unlock] is never invoked before a successful
     * lock acquisition.
     */
    public fun unlock() 
}

/**
 * Executes the given code [block] under this mutex's lock.
 * 
 * @return result of [block]
 */
@OptIn(ExperimentalContracts::class)
public inline fun <T> SynchronousMutex.withLock(block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}
