package internal_test2

import kotlinx.atomicfu.*
import kotlinx.atomicfu.test.A
import kotlin.test.*

class C {
    @Test
    fun testInternal() {
        val a = A()
        check(a.yyy.decrementAndGet() == 5L)
    }
}