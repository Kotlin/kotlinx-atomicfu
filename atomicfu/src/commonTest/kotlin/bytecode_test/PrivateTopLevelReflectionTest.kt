@file:JvmName("PrivateTopLevel")

package bytecode_test

import kotlinx.atomicfu.atomic
import kotlin.jvm.JvmName

private val b = atomic(2)

class PrivateTopLevelReflectionTest {
    fun update() {
        val _ = b.compareAndSet(0, 42)
    }
}
