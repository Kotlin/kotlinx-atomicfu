package kotlinx.atomicfu.locks

import kotlin.time.Duration

/**
 * Multiplatform mutex.
 * Since this mutex will run in a single threaded environment, it doesn't provide any real synchronization.
 * 
 * It does keep track of reentrancy.
 */
public actual class SynchronousMutex {
    private var state = 0
    public actual fun tryLock(): Boolean = true
    public actual fun tryLock(timeout: Duration): Boolean = true
    public actual fun lock(): Unit { state++ }
    public actual fun unlock(): Unit { 
        check(state > 0) { "Mutex already unlocked" }
        state--
    }
}