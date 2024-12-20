package kotlinx.atomicfu.locks

import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * This mutex uses a [ReentrantLock].
 * 
 * Construct with `Mutex(reentrantLock)` to create a [SynchronousMutex] that uses an existing instance of [ReentrantLock].
 */
public actual class SynchronousMutex {
    private val reentrantLock = ReentrantLock()
    public actual fun tryLock(timeout: Duration): Boolean = reentrantLock.tryLock(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    public actual fun tryLock(): Boolean = reentrantLock.tryLock()
    public actual fun lock(): Unit = reentrantLock.lock()
    public actual fun unlock(): Unit = reentrantLock.unlock()
}