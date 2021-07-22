package kotlinx.atomicfu.test

import kotlinx.atomicfu.*

class EqualLocalVariablesTest {

    fun testLVTTransformation() {
        val holder1 = AtomicIntHolder(1)
        val holder2 = AtomicIntHolder(2)
        holder1.atomic.foo(2)
        holder2.atomic.foo(1)
    }

    private inline fun AtomicInt.foo(to: Int): Boolean = loop { cur ->
        return cur == to
    }

    private class AtomicIntHolder(n: Int) {
        val atomic = atomic(n)
    }
}