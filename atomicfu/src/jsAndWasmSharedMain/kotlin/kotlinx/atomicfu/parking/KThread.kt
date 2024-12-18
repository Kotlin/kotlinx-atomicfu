package kotlinx.atomicfu.parking

actual class KThread internal actual constructor() {
    actual companion object {
        actual fun currentThread() = thisKthread
    }
}

actual class Parker actual private constructor() {
    actual companion object {
        actual fun park() {}
        actual fun parkNanos(nanos: Long) {}
        actual fun unpark(kThread: KThread) {}
    }
}
private val thisKthread = KThread()
actual fun currentThreadId(): Long = 1