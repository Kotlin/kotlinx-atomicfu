package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic

class LockFreeLongCounter {
    private val counter = atomic(0L)

    fun get(): Long = counter.value

    fun increment(): Long = counter.incrementAndGet()

    fun getInner(): Long = Inner().getFromOuter()

    // testing how an inner class can get access to it
    private inner class Inner {
        fun getFromOuter(): Long = counter.value
    }
}