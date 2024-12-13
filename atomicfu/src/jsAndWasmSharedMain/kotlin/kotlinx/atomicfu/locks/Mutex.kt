package kotlinx.atomicfu.locks

actual class Mutex {
    private var state = 0
    actual fun isLocked(): Boolean = state != 0
    actual fun tryLock(): Boolean = true
    actual fun lock(): Unit { state++ }
    actual fun unlock(): Unit { if (state-- < 0) throw IllegalStateException("Mutex already unlocked") }
}