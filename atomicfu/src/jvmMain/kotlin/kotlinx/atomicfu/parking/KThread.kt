package kotlinx.atomicfu.parking

actual class KThread internal actual constructor() {
    internal val parker = ThreadParker(JvmParkingDelegator())
    actual companion object {
        actual fun currentThread(): KThread = localKThread.get()
    }
}

actual class Parker actual private constructor() {
    actual companion object {
        actual fun park() = localKThread.get().parker.park()
        actual fun parkNanos(nanos: Long) = localKThread.get().parker.parkNanos(nanos)
        actual fun unpark(kThread: KThread) = kThread.parker.unpark()
    }
}

private val localKThread = ThreadLocal<KThread>.withInitial { KThread() }
actual fun currentThreadId(): Long = Thread.currentThread().id
