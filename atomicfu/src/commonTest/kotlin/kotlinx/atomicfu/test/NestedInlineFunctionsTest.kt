package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

// Ensures that inline function receivers are resolved correctly by the JS transformer
class NestedInlineFunctionsTest {
    val _a = atomic(5)
    val _b = atomic(42)

    @Test
    fun testNestedInlineFunctions() {
        _a.loop { a ->
            _b.loop { b ->
                assertEquals(5, a)
                assertEquals(42, b)
                return
            }
        }
    }
}