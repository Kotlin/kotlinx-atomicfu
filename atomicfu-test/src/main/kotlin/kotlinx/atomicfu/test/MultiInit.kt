package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic

class MultiInit {
    private val a = atomic(0)
    private val b = atomic(0)

    fun incA() = a.incrementAndGet()
    fun incB() = b.incrementAndGet()

    companion object {
        fun foo() {} // just to force some clinit in outer file
    }
}