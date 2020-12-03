package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

class NestedAtomicFieldLoads {
    private val a = atomic(0)
    private val b = atomic(1)
    private val c = atomic(2)
    private val ref = atomic(A(B(70)))

    private val flag = atomic(false)

    private val arr = AtomicIntArray(7)

    private fun foo(arg1: Int, arg2: Int, arg3: Int, arg4: Int): Int = arg1 + arg2 + arg3 + arg4

    private class A(val b: B)
    private class B(val n: Int)

    @Test
    fun testNestedGetField() {
        a.value = b.value + c.value
        assertEquals(3, a.value)
        a.value = foo(a.value, b.value, c.value, ref.value.b.n)
        assertEquals(76, a.value)
    }

    @Test
    fun testNestedAtomicInvocations() {
        flag.value = a.compareAndSet(0, 56)
        assertTrue(flag.value)
    }

    @Test
    fun testArrayNestedLoads() {
        arr[5].value = b.value
        assertEquals(1, arr[5].value)
        arr[0].value = foo(arr[5].value, b.value, c.value, ref.value.b.n)
        assertEquals(74, arr[0].value)
    }
}