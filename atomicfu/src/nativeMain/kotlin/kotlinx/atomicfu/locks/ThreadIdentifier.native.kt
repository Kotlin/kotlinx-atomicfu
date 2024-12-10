package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic

private val threadCounter = atomic(0L)

@kotlin.native.concurrent.ThreadLocal
private var threadId: Long = threadCounter.addAndGet(1)

internal actual fun currentThreadId(): Long = threadId
