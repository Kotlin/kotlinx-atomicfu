package kotlinx.atomicfu.locks

import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * This mutex uses a [ReentrantLock].
 * 
 * Construct with `Mutex(reentrantLock)` to create a [SynchronousMutex] that uses an existing instance of [ReentrantLock].
 */
actual class SynchronousMutex {
    private val reentrantLock = ReentrantLock()
    actual fun tryLock(timeout: Duration): Boolean = reentrantLock.tryLock(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    actual fun tryLock(): Boolean = reentrantLock.tryLock()
    actual fun lock() = reentrantLock.lock()
    actual fun unlock() = reentrantLock.unlock()
}