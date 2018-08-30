package internal_test2

import internal_test1.D
import kotlinx.atomicfu.*
import kotlinx.atomicfu.test.A
import kotlin.test.*

class C {
    @Test
    fun testInternal() {
        val a = A()
        check(a.yyy.decrementAndGet() == 638753975930025819)
        check(a.arr[3].getAndAdd(5) == 0)
        val d = D()
        check(d.da.arr[2].compareAndSet(0, 38535))
        check(d.da.xxx.getAndAdd(90) == 5)
        check(d.da.xxx.value == 95)
    }
}