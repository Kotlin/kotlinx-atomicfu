package kotlinx.atomicfu.test

import kotlin.test.Test
import kotlin.test.assertEquals

class IndexArrayElementGetter {
    private val clazz = AtomicArrayClass()

    fun fib(a: Int): Int = if (a == 0 || a == 1) a else fib(a - 1) + fib(a - 2)

    @Test
    fun testIndexArrayElementGetting() {
        clazz.intArr[8].value = 3
        val i = fib(4)
        val j = fib(5)
        assertEquals(clazz.intArr[i + j].value, 3)
        assertEquals(clazz.intArr[fib(4) + fib(5)].value, 3)
        clazz.longArr[3].value = 100
        assertEquals(clazz.longArr[fib(6) - fib(5)].value, 100)
        assertEquals(clazz.longArr[(fib(6) + fib(4)) % 8].value, 100)
        assertEquals(clazz.longArr[(fib(6) + fib(4)) % 8].value, 100)
        assertEquals(clazz.longArr[(fib(4) + fib(5)) % fib(5)].value, 100)
    }
}