package kotlinx.atomicfu.locks

import interop.*
import kotlinx.atomicfu.*

/**
 * This is internal API for internal uses and eventually it will break.
 * Avoid using it in your projects, it is undocumented deliberately.
 */
@ExperimentalConcurrencyApi
public class NonReentrantLock {

    // CAS-ed locked attribute to fail fast instead of UB in case of misuse
    private val isLocked = atomic(false)
    // Same, but for destroy
    private val isPoisoned = atomic(false)
    private val fatLock = fat_lock_init()

    public inner class Condition() {
        private val condition = fat_lock_condition_init()

        fun wait() {
            unlockState()
            cond_wait(fatLock, condition)
            lockState()
        }

        fun notifyAll() {
            cond_notify_all(condition)
        }

        fun notifyOne() {
            cond_notify_one(condition)
        }

        fun destroy() {
            fat_lock_condition_destroy(condition)
        }
    }

    public fun lock() {
        checkPoisoned()
        lock_fat(fatLock)
        lockState()
    }

    public fun unlock() {
        checkPoisoned()
        unlockState()
        unlock_fat(fatLock)
    }

    public fun destroy() {
        isPoisoned.value = true
        fat_lock_destroy(fatLock)
    }

    private fun lockState() {
        require(isLocked.compareAndSet(false, true)) { "Mutex should not be locked" }
    }

    private fun unlockState() {
        require(isLocked.compareAndSet(true, false)) { "Mutex should be locked" }
    }

    private fun checkPoisoned() = require(!isPoisoned.value) { "Mutex was already destroyed" }
}

public inline fun <T> NonReentrantLock.withLock(block: () -> T): T {
    try {
        lock()
        return block()
    } finally {
        unlock()
    }
}
