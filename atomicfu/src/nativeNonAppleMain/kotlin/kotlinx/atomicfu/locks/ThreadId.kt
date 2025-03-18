package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic

private val threadCounter = atomic(0L)

internal actual fun createThreadId(): Long = threadCounter.incrementAndGet()
