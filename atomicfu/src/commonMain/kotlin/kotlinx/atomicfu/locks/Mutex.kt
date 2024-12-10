package kotlinx.atomicfu.locks

/**
 * Multiplatform mutex.
 * On native based on futex(-like) system calls.
 * On JVM delegates to ReentrantLock.
 */
expect class Mutex {
    fun isLocked(): Boolean
    fun tryLock(): Boolean
    fun lock()
    fun unlock()
}

fun <T> Mutex.withLock(block: () -> T): T {
    lock()
    val result = block()
    unlock()
    return result
}