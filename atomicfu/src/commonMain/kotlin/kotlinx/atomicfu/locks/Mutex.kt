package kotlinx.atomicfu.locks

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Multiplatform mutex.
 * On native based on futex(-like) system calls.
 * On JVM delegates to ReentrantLock.
 */
expect class Mutex() {
    fun isLocked(): Boolean
    fun tryLock(): Boolean
    fun lock()
    fun unlock()
}
@OptIn(ExperimentalContracts::class)
fun <T> Mutex.withLock(block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

