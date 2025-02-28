package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic

private val threadCounter = atomic(0L)

actual fun createThreadId(): Long = threadCounter.incrementAndGet()
