package kotlinx.atomicfu.locks

import kotlin.time.Duration

actual class SynchronousMutex {
    private val lock = NativeMutex()
    actual fun tryLock() = lock.tryLock()
    actual fun tryLock(timeout: Duration) = lock.tryLock(timeout)
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}