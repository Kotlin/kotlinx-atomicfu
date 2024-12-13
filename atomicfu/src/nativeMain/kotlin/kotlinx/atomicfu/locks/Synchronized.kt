package kotlinx.atomicfu.locks

public actual open class SynchronizedObject {
    private val lock = NativeMutex { PosixParkingDelegator }
    public fun lock() = lock.lock()
    public fun tryLock() = lock.tryLock()
    public fun unlock()  = lock.unlock()
}

public actual fun reentrantLock() = ReentrantLock()

public actual typealias ReentrantLock = SynchronizedObject

public actual inline fun <T> ReentrantLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T {
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}
