@file:JvmName("PublicTopLevel")

package bytecode_test

import kotlinx.atomicfu.*
import kotlin.jvm.JvmName

internal val c = atomic(0)

class PublicTopLevelReflectionTest {
    fun update() {
        d.lazySet(56)
    }
}