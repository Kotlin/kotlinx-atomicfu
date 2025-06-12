package kotlinx.atomicfu.locks

import kotlin.time.Duration

public actual class SynchronousMutex {
    private val lock = NativeMutex()
    public actual fun tryLock(): Boolean = lock.tryLock()
    public actual fun tryLock(timeout: Duration): Boolean = lock.tryLock(timeout)
    public actual fun lock(): Unit = lock.lock()
    public actual fun unlock(): Unit = lock.unlock()
}