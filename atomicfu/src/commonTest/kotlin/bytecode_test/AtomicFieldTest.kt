package bytecode_test

import kotlinx.atomicfu.*
import kotlin.test.*

class AtomicFieldTest {
    private val _state = atomic(0)

    @Test
    fun testAtomicField() {
        assertEquals(0, _state.value)
        assertTrue(_state.compareAndSet(0, 42))
        assertEquals(42, _state.value)
    }
}