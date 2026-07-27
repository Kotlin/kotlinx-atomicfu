package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetArrayElementTest {
    @Test
    fun testGetArrayField() {
        val aes = ArrayElementSetters()
        assertTrue(aes.setInt(2, 5))
        assertFalse(aes.setInt(2, 10))
        assertTrue(aes.setBoolean(1, true))
        assertTrue(aes.setRef(1, IntBox(29472395)))
        assertFalse(aes.setRef(1, IntBox(81397)))
    }

    @Test
    fun testTransformInMethod() {
        val holder = AtomicArrayWithMethod()
        holder.set("Hello", 0)
    }
}

class ArrayElementSetters {
    @Suppress("DEPRECATION_ERROR")
    private val intArr = kotlinx.atomicfu.AtomicIntArray(3)
    @Suppress("DEPRECATION_ERROR")
    private val booleanArr = kotlinx.atomicfu.AtomicBooleanArray(4)
    @Suppress("DEPRECATION_ERROR")
    private val refArr = atomicArrayOfNulls<IntBox>(5)

    fun setInt(index: Int, data: Int) = intArr[index].compareAndSet(0, data)
    fun setBoolean(index: Int, data: Boolean) = booleanArr[index].compareAndSet(false, data)
    fun setRef(index: Int, data: IntBox) = refArr[index].compareAndSet(null, data)
}

class AtomicArrayWithMethod {
    @Suppress("DEPRECATION_ERROR")
    val refArray = atomicArrayOfNulls<String>(5)

    fun set(data: String, index: Int) {
        val result = refArray[index].compareAndSet(null, data)
        if (!result) error("Double set")
    }
}
