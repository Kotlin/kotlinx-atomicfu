package kotlinx.atomicfu.locks

import java.util.concurrent.TimeUnit
import kotlin.time.Duration

public actual class SynchronousMutex {
    private val reentrantLock = ReentrantLock()
    public actual fun tryLock(timeout: Duration): Boolean = reentrantLock.tryLock(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    public actual fun tryLock(): Boolean = reentrantLock.tryLock()
    public actual fun lock(): Unit = reentrantLock.lock()
    public actual fun unlock(): Unit = reentrantLock.unlock()
}