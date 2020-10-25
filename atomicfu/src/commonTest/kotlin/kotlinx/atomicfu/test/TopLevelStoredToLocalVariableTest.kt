package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.Test
import kotlin.test.assertEquals

private val top = atomic(0)
private val topArr = AtomicIntArray(5)

class TopLevelStoredToLocalVariableTest {

    @Test
    fun testTopLevelArrayElementUpdate() {
        topArr[3].update { 55 }
        assertEquals(55, topArr[3].getAndUpdate { 66 })
        assertEquals(77, topArr[3].updateAndGet { 77 })
        topArr[3].loop {  value ->
            if (value == 77) topArr[3].compareAndSet(value, 66)
            assertEquals(66, topArr[3].value)
            return
        }
    }

    @Test
    fun testTopLevelUpdate() {
        top.update { 5 }
        assertEquals(5, top.getAndUpdate { 66 })
        assertEquals(77, top.updateAndGet { 77 })
        top.loop { value ->
            assertEquals(77, value)
            if (value == 77) top.compareAndSet(value, 66)
            assertEquals(66, top.value)
            return
        }
    }

    @Test
    fun testObjectFieldUpdate() {
        Example.update()
        assertEquals("test !", Example.x)
    }
}

object Example {
    private val _x = atomic("test")
    val x get() = _x.value

    fun update() {
        _x.getAndUpdate { "$it !" }
    }
}