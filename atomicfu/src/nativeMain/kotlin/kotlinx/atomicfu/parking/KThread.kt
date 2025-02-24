package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic

actual class KThread internal actual constructor() {
    internal val parker: ThreadParker = ThreadParker()
    actual companion object {
        actual fun currentThread(): KThread = thisKThread
    }
}

actual class Parker private actual constructor() {
    actual companion object {
        actual fun park() = thisKThread.parker.park()
        actual fun parkNanos(nanos: Long) = thisKThread.parker.parkNanos(nanos)
        actual fun unpark(kThread: KThread) = kThread.parker.unpark()
    }
}
@kotlin.native.concurrent.ThreadLocal
private val thisKThread: KThread = KThread()

