@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic
import kotlin.test.*

class DelegatedProperties {

    private val _a = atomic(42)
    var a: Int by _a

    private val _l = atomic(55555555555)
    var l: Long by _l

    private val _b = atomic(false)
    var b: Boolean by _b

    private val _ref = atomic(A(B(77)))
    var ref: A by _ref

    var vInt by atomic(77)

    var vLong by atomic(777777777)

    var vBoolean by atomic(false)

    var vRef by atomic(A(B(77)))

    @Test
    fun testDelegatedAtomicInt() {
        assertEquals(42, a)
        _a.compareAndSet(42, 56)
        assertEquals(56, a)
        a = 77
        _a.compareAndSet(77,  66)
        assertEquals(66, _a.value)
        assertEquals(66, a)
    }

    @Test
    fun testDelegatedAtomicLong() {
        assertEquals(55555555555, l)
        _l.getAndIncrement()
        assertEquals(55555555556, l)
        l = 7777777777777
        assertTrue(_l.compareAndSet(7777777777777, 66666666666))
        assertEquals(66666666666, _l.value)
        assertEquals(66666666666, l)
    }

    @Test
    fun testDelegatedAtomicBoolean() {
        assertEquals(false, b)
        _b.lazySet(true)
        assertEquals(true, b)
        b = false
        assertTrue(_b.compareAndSet(false, true))
        assertEquals(true, _b.value)
        assertEquals(true, b)
    }

    @Test
    fun testDelegatedAtomicRef() {
        assertEquals(77, ref.b.n)
        _ref.lazySet(A(B(66)))
        assertEquals(66, ref.b.n)
        assertTrue(_ref.compareAndSet(_ref.value, A(B(56))))
        assertEquals(56, ref.b.n)
        ref = A(B(99))
        assertEquals(99, _ref.value.b.n)
    }

    @Test
    fun testVolatileInt() {
        assertEquals(77, vInt)
        vInt = 55
        assertEquals(110, vInt * 2)
    }

    @Test
    fun testVolatileLong() {
        assertEquals(777777777, vLong)
        vLong = 55
        assertEquals(55, vLong)
    }

    @Test
    fun testVolatileBoolean() {
        assertEquals(false, vBoolean)
        vBoolean = true
        assertEquals(true, vBoolean)
    }

    @Test
    fun testVolatileRef() {
        assertEquals(77, vRef.b.n)
        vRef = A(B(99))
        assertEquals(99, vRef.b.n)
    }

    class A (val b: B)
    class B (val n: Int)
}

class ExposedDelegatedPropertiesAccessorsTest {

    private inner class A {
        private val _node = atomic<Node?>(null)
        var node: Node? by _node

        fun cas(expect: Node, update: Node) = _node.compareAndSet(expect, update)
    }

    private class Node(val n: Int)

    @Test
    fun testDelegatedPropertiesAccessors() {
        val a = A()
        val update = Node(5)
        a.node = update
        assertTrue(a.cas(update, Node(6)))
        assertEquals(6, a.node?.n)
    }

    @Test
    fun testAccessors() {
        val cl = DelegatedProperties()
        assertEquals(42, cl.a)
        cl.a = 66
        assertEquals(66, cl.a)
        assertEquals(55555555555, cl.l)
        cl.l = 66666666
        assertEquals(66666666, cl.l)
        assertEquals(false, cl.b)
        cl.b = true
        assertEquals(true, cl.b)
    }

    @Test
    fun testVolatileProperties() {
        val cl = DelegatedProperties()
        assertEquals(77, cl.vInt)
        cl.vInt = 99
        assertEquals(99, cl.vInt)
    }
}