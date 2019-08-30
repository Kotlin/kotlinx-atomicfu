package kotlinx.atomicfu.test

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val topLevelS = atomic<Any>(listOf("A", "B"))

class UncheckedCastTest {
    private val s = atomic<Any>("AAA")
    private val bs = atomic<Any?>(null)

    @Test
    fun testAtomicValUncheckedCast() {
        assertEquals((s as AtomicRef<String>).value, "AAA")
        bs.lazySet(mapOf(1 to listOf(Box(1), Box(2))))
        assertEquals((bs as AtomicRef<Map<Int, List<Box>>>).value[1]!![0].b * 10, 10)
    }

    @Test
    fun testTopLevelValUnchekedCast() {
        assertEquals((topLevelS as AtomicRef<List<String>>).value[1], "B")
    }

    private data class Box(val b: Int)

    private inline fun <T> AtomicRef<T>.getString(): String =
        (this as AtomicRef<String>).value

    @Test
    fun testInlineFunc() {
        assertEquals("AAA", s.getString())
    }

    private val a = atomicArrayOfNulls<Any?>(10)

    @Test
    fun testArrayValueUncheckedCast() {
        a[0].value = "OK"
        assertEquals("OK", (a[0] as AtomicRef<String>).value)
    }

    @Test
    fun testArrayValueUncheckedCastInlineFunc() {
        a[0].value = "OK"
        assertEquals("OK", a[0].getString())
    }
}