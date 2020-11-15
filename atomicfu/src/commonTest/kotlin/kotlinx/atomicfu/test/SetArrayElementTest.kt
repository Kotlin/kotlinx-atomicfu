package kotlinx.atomicfu.test

import kotlinx.atomicfu.AtomicBooleanArray
import kotlinx.atomicfu.AtomicIntArray
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
    private val intArr = AtomicIntArray(3)
    private val booleanArr = AtomicBooleanArray(4)
    private val refArr = atomicArrayOfNulls<IntBox>(5)

    fun setInt(index: Int, data: Int) = intArr[index].compareAndSet(0, data)
    fun setBoolean(index: Int, data: Boolean) = booleanArr[index].compareAndSet(false, data)
    fun setRef(index: Int, data: IntBox) = refArr[index].compareAndSet(null, data)
}

class AtomicArrayWithMethod {
    val refArray = atomicArrayOfNulls<String>(5)

    fun set(data: String, index: Int) {
        val result = refArray[index].compareAndSet(null, data)
        if (!result) error("Double set")
    }
}
