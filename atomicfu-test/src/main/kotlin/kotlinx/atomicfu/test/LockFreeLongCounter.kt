package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic

class LockFreeLongCounter {
    private val counter = atomic(0L)

    fun get(): Long = counter.value

    fun increment(): Long {
        return counter.incrementAndGet()
    }
}