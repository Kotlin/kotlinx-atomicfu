package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

/**
 * Tests atomic fields that work as replacement to volatiles (only getValue/setValue)
 */
class VolatileOnlyTest {
    private val _ref = atomic<String?>(null)
    private val _int = atomic(0)

    @Test
    fun testVolatileOnly() {
        _ref.value = "OK"
        assertEquals("OK", _ref.value)
        _int.value = 42
        assertEquals(42, _int.value)
    }
}   