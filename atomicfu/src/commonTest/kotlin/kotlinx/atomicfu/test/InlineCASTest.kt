package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.Test
import kotlin.test.assertEquals

class InlineCASTest {

    private val a = atomic(0)
    private val ref = atomic(listOf("AAA"))

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AtomicInt.casLoop(to: Int): Int = loop { cur ->
        if (compareAndSet(cur, to)) return value
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AtomicRef<List<String>>.casLoop(to: List<String>): List<String> = loop { cur ->
        if (compareAndSet(cur, to)) return value
    }

    @Test
    fun testLocalVariableScope() {
        assertEquals(a.casLoop(5), 5)
        assertEquals(ref.casLoop(listOf("BBB")), listOf("BBB"))
    }
}