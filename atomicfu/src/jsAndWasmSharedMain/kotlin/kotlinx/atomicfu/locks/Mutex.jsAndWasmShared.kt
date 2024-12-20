package kotlinx.atomicfu.locks

import kotlin.time.Duration

/**
 * Part of multiplatform mutex.
 * Since this mutex will run in a single threaded environment, it doesn't provide any real synchronization.
 * 
 * It does keep track of reentrancy.
 */
actual class SynchronousMutex {
    private var state = 0
    actual fun tryLock(): Boolean = true
    actual fun tryLock(timeout: Duration): Boolean = true
    actual fun lock(): Unit { state++ }
    actual fun unlock(): Unit { if (state-- < 0) throw IllegalStateException("Mutex already unlocked") }
}