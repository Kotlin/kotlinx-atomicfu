package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomicLong

class LockFreeLongCounter {
    private val counter = atomicLong()

    fun increment(): Long {
        return counter.incrementAndGet()
    }
}