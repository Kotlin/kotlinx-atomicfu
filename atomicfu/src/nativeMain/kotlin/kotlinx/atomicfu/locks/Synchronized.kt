package kotlinx.atomicfu.locks

public actual open class SynchronizedObject {
    
    private val nativeMutex = SynchronousMutex()
    public fun lock(): Unit = nativeMutex.lock()
    public fun tryLock(): Boolean = nativeMutex.tryLock()
    public fun unlock(): Unit = nativeMutex.unlock()
}

public actual fun reentrantLock(): ReentrantLock = ReentrantLock()

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