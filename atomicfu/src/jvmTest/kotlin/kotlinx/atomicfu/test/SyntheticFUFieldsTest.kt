package kotlinx.atomicfu.test

import bytecode_test.PrivateFieldAccessFromInnerClassReflectonTest
import java.lang.reflect.Modifier.*
import kotlin.test.Test

private const val AFU_TYPE = "java.util.concurrent.atomic.AtomicIntegerFieldUpdater"

/**
 * Checks that generated FU and VH field updaters are marked as synthetic
 */
class SyntheticFUFieldsTest : ReflectionTestBase() {
    @Test
    fun testPrivateFieldAccessFromInnerClass() {
        checkDeclarations(PrivateFieldAccessFromInnerClassReflectonTest::class.java, listOf(
                FieldDesc(VOLATILE, true, "int", "state"),
                FieldDesc(STATIC or FINAL, true, AFU_TYPE, "state\$FU")
            )
        )
    }
}