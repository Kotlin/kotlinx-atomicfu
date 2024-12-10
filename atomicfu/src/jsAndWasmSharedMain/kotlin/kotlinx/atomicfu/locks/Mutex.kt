package kotlinx.atomicfu.locks

actual class Mutex {
    private var locked = false
    actual fun isLocked(): Boolean = locked
    actual fun tryLock(): Boolean = true
    actual fun lock(): Unit { locked = true }
    actual fun unlock(): Unit { locked = false }
}