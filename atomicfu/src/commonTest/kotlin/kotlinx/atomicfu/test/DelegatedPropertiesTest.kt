@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic
import kotlin.test.*

private val topLevelIntOriginalAtomic = atomic(77)
var topLevelIntDelegatedProperty: Int by topLevelIntOriginalAtomic

private val _topLevelLong = atomic(55555555555)
var topLevelDelegatedPropertyLong: Long by _topLevelLong

private val _topLevelBoolean = atomic(false)
var topLevelDelegatedPropertyBoolean: Boolean by _topLevelBoolean

private val _topLevelRef = atomic(listOf("a", "b"))
var topLevelDelegatedPropertyRef: List<String> by _topLevelRef

var vTopLevelInt by atomic(77)

var vTopLevelLong by atomic(777777777)

var vTopLevelBoolean by atomic(false)

var vTopLevelRef by atomic(listOf("a", "b"))

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

    @Test
    fun testTopLevelDelegatedPropertiesInt() {
        assertEquals(77, topLevelIntDelegatedProperty)
        topLevelIntOriginalAtomic.compareAndSet(77, 56)
        assertEquals(56, topLevelIntDelegatedProperty)
        topLevelIntDelegatedProperty = 88
        topLevelIntOriginalAtomic.compareAndSet(88,  66)
        assertEquals(66, topLevelIntOriginalAtomic.value)
        assertEquals(66, topLevelIntDelegatedProperty)
    }

    @Test
    fun testTopLevelDelegatedPropertiesLong() {
        assertEquals(55555555555, topLevelDelegatedPropertyLong)
        _topLevelLong.getAndIncrement()
        assertEquals(55555555556, topLevelDelegatedPropertyLong)
        topLevelDelegatedPropertyLong = 7777777777777
        assertTrue(_topLevelLong.compareAndSet(7777777777777, 66666666666))
        assertEquals(66666666666, _topLevelLong.value)
        assertEquals(66666666666, topLevelDelegatedPropertyLong)
    }

    @Test
    fun testTopLevelDelegatedPropertiesBoolean() {
        assertEquals(false, topLevelDelegatedPropertyBoolean)
        _topLevelBoolean.lazySet(true)
        assertEquals(true, topLevelDelegatedPropertyBoolean)
        topLevelDelegatedPropertyBoolean = false
        assertTrue(_topLevelBoolean.compareAndSet(false, true))
        assertEquals(true, _topLevelBoolean.value)
        assertEquals(true, topLevelDelegatedPropertyBoolean)
    }

    @Test
    fun testTopLevelDelegatedPropertiesRef() {
        assertEquals("b", topLevelDelegatedPropertyRef[1])
        _topLevelRef.lazySet(listOf("c"))
        assertEquals("c", topLevelDelegatedPropertyRef[0])
        topLevelDelegatedPropertyRef = listOf("d", "e")
        assertEquals("e", _topLevelRef.value[1])
    }

    @Test
    fun testVolatileTopLevelInt() {
        assertEquals(77, vTopLevelInt)
        vTopLevelInt = 55
        assertEquals(110, vTopLevelInt * 2)
    }

    @Test
    fun testVolatileTopLevelLong() {
        assertEquals(777777777, vTopLevelLong)
        vTopLevelLong = 55
        assertEquals(55, vTopLevelLong)
    }

    @Test
    fun testVolatileTopLevelBoolean() {
        assertEquals(false, vTopLevelBoolean)
        vTopLevelBoolean = true
        assertEquals(true, vTopLevelBoolean)
    }

    @Test
    fun testVolatileTopLevelRef() {
        assertEquals("a", vTopLevelRef[0])
        vTopLevelRef = listOf("c")
        assertEquals("c", vTopLevelRef[0])
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

class ClashedNamesTest {
    private class A1 {
        val _a = atomic(0)
        val a: Int by _a
    }

    private class A2 {
        val _a = atomic(0)
        val a: Int by _a
    }

    @Test
    fun testClashedDelegatedPropertiesNames() {
        val a1Class = A1()
        val a2Class = A2()
        a1Class._a.compareAndSet(0, 77)
        assertEquals(77, a1Class.a)
        assertEquals(0, a2Class.a)
    }
}