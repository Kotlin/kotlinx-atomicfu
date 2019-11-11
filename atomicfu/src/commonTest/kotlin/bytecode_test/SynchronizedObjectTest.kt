package bytecode_test

import kotlinx.atomicfu.locks.*
import kotlin.test.*

class SynchronizedObjectTest : SynchronizedObject() {
    @Test
    fun testSync() {
        val result = synchronized(this) { bar() }
        assertEquals("OK", result)
    }

    private fun bar(): String =
        synchronized(this) {
            "OK"
        }
}
