package kotlinx.atomicfu.locks

actual class Mutex {
    private val mutex = NativeMutex { PosixParkingDelegator }
    actual fun isLocked(): Boolean = mutex.isLocked()
    actual fun tryLock(): Boolean = mutex.tryLock()
    actual fun lock() = mutex.lock()
    actual fun unlock() = mutex.unlock()
}
