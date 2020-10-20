/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

class ArrayTest {
    @Test
    fun testIntArray() {
        val A = AtomicArrayClass()
        check(A.intArr.size == 10)
        check(A.intArr[0].compareAndSet(0, 3))
        check(A.intArr[1].value == 0)
        A.intArr[0].lazySet(5)
        check(A.intArr[0].value + A.intArr[1].value + A.intArr[2].value == 5)
        check(A.intArr[0].compareAndSet(5, 10))
        check(A.intArr[0].getAndDecrement() == 10)
        check(A.intArr[0].value == 9)
        A.intArr[2].value = 2
        check(A.intArr[2].value == 2)
        check(A.intArr[2].compareAndSet(2, 34))
        check(A.intArr[2].value == 34)
        assertEquals(20, A.intArr.size + A.longArr.size)
        val size = A.intArr.size
        var sum = 0
        for (i in 0 until size) { sum += A.intArr[i].value }
        assertEquals(43, sum)
    }

    @Test
    fun testLongArray() {
        val A = AtomicArrayClass()
        check(A.longArr.size == 10)
        A.longArr[0].value = 2424920024888888848
        check(A.longArr[0].value == 2424920024888888848)
        A.longArr[0].lazySet(8424920024888888848)
        check(A.longArr[0].value == 8424920024888888848)
        val ac = A.longArr[0].value
        A.longArr[3].value = ac
        check(A.longArr[3].getAndSet(8924920024888888848) == 8424920024888888848)
        check(A.longArr[3].value == 8924920024888888848)
        val ac1 = A.longArr[3].value
        A.longArr[4].value = ac1
        check(A.longArr[4].incrementAndGet() == 8924920024888888849)
        check(A.longArr[4].value == 8924920024888888849)
        check(A.longArr[4].getAndDecrement() == 8924920024888888849)
        check(A.longArr[4].value == 8924920024888888848)
        A.longArr[4].value = 8924920024888888848
        check(A.longArr[4].getAndAdd(100000000000000000) == 8924920024888888848)
        val ac2 = A.longArr[4].value
        A.longArr[1].value = ac2
        check(A.longArr[1].value == 9024920024888888848)
        check(A.longArr[1].addAndGet(-9223372036854775807) == -198452011965886959)
        check(A.longArr[1].value == -198452011965886959)
        check(A.longArr[1].incrementAndGet() == -198452011965886958)
        check(A.longArr[1].value == -198452011965886958)
        check(A.longArr[1].decrementAndGet() == -198452011965886959)
        check(A.longArr[1].value == -198452011965886959)
    }

    @Test
    fun testBooleanArray() {
        val A = AtomicArrayClass()
        check(A.booleanArr.size == 10)
        check(!A.booleanArr[1].value)
        A.booleanArr[1].compareAndSet(false, true)
        A.booleanArr[0].lazySet(true)
        check(!A.booleanArr[2].getAndSet(true))
        check(A.booleanArr[0].value && A.booleanArr[1].value && A.booleanArr[2].value)
        A.booleanArr[0].value = false
        check(!A.booleanArr[0].value)
    }

    @Test
    fun testRefArray() {
        val A = AtomicArrayClass()
        check(A.refArr.size == 10)
        check(A.genericArr.size == 10)
        val a2 = IntBox(2)
        val a3 = IntBox(3)
        A.refArr[0].value = a2
        check(A.refArr[0].value!!.n == 2)
        check(A.refArr[0].compareAndSet(a2, a3))
        check(A.refArr[0].value!!.n == 3)
        val r0 = A.refArr[0].value
        A.refArr[3].value = r0
        check(A.refArr[3].value!!.n == 3)
        val a = A.a.value
        check(A.refArr[3].compareAndSet(a3, a))

        val l1 = listOf(listOf("a", "bb", "ccc"), listOf("a", "bb"))
        val l2 = listOf(listOf("1", "22", "333"), listOf("1", "22"))
        A.genericArr[2].lazySet(l1)
        check(A.genericArr[2].compareAndSet(l1, l2))

        A.mapArr[0].value = mapOf(listOf("A", "B") to "C")
    }

    @Test
    fun extendedApiTest() {
        val ea = ExtendedApiAtomicArrays()
        check(ea.stringAtomicNullArray[0].value == null)
        check(ea.stringAtomicNullArray[0].compareAndSet(null, "aaa"))
        val totalString = buildString {
            for (i in 0 until ea.stringAtomicNullArray.size) {
                ea.stringAtomicNullArray[i].value?.let { append(it) }
            }
        }
        assertEquals("aaa", totalString)

        check(ea.genAtomicNullArr[3].value == null)
        val l1 = listOf("a", "bb", "ccc")
        val l2 = listOf("dddd")
        val l3 = listOf("a", "bb", "ccc", "dddd")
        check(ea.genAtomicNullArr[3].compareAndSet(null, l1))
        assertEquals(l1, ea.genAtomicNullArr[3].value)
        check(ea.genAtomicNullArr[3].compareAndSet(l1, l2))
        assertEquals(l2, ea.genAtomicNullArr[3].value)
        ea.genAtomicNullArr[2].lazySet(l3)
        assertEquals(l3, ea.genAtomicNullArr[2].value)
    }
}

class AtomicArrayClass {
    val intArr = AtomicIntArray(10)
    val longArr = AtomicLongArray(10)
    val booleanArr = AtomicBooleanArray(10)
    val refArr = AtomicArray<IntBox>(10)
    val genericArr = AtomicArray<List<List<String>>>(10)
    val mapArr = atomicArrayOfNulls<Map<List<String>, String>>(10)
    val anyArr = atomicArrayOfNulls<Any?>(10)
    val a = atomic(IntBox(8))
}

class ExtendedApiAtomicArrays {
    val stringAtomicNullArray = atomicArrayOfNulls<String>(10)
    val genAtomicNullArr = atomicArrayOfNulls<List<String>>(7)
}

data class IntBox(val n: Int)

