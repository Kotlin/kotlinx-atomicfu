package kotlinx.atomicfu.locks

import java.util.concurrent.locks.ReentrantLock

actual class Mutex actual constructor() {
    val reentrantLock: ReentrantLock = ReentrantLock()
    actual fun isLocked(): Boolean = reentrantLock.isLocked
    actual fun tryLock(): Boolean = reentrantLock.tryLock()
    actual fun lock() = reentrantLock.lock()
    actual fun unlock() = reentrantLock.unlock()
}
