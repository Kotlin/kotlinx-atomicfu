package internal_test1

import kotlinx.atomicfu.*
import kotlinx.atomicfu.test.A
import kotlin.test.*

class B {
    @Test
    fun testInternal() {
        val a = A()
        a.internalField.lazySet(true)
        check(a.xxx.addAndGet(4) == 9)
        val b = LocalClass()
        b.local.lazySet(false)
        val localAarr = a.arr
        a.arr[2].compareAndSet(0, 6)
    }

    inner class LocalClass {
        val local = atomic(false)
        fun lazySet(v: Boolean) = local.lazySet(v)
    }
}
