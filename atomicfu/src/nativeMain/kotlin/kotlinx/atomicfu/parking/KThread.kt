package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic

actual class KThread internal actual constructor() {
    internal val parker: ThreadParker = ThreadParker(PosixParkingDelegator)
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

private val threadCounter = atomic(0L)

@kotlin.native.concurrent.ThreadLocal
private var threadId: Long = threadCounter.addAndGet(1)

internal actual fun currentThreadId(): Long = threadId
