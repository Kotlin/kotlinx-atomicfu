package kotlinx.atomicfu.test

import org.junit.*

/**
 * Makes sure [VolatileOnlyTest] does not have FU/VH usages in bytecode
 */
class VolatileOnlyBytecodeTest {
    private val strings = listOf("FieldUpdater", "VarHandle")

    @Test
    fun testBytecode() {
        val javaClass = VolatileOnlyTest::class.java
        val resourceName = javaClass.name.replace('.', '/') + ".class"
        val bytes = javaClass.classLoader.getResourceAsStream(resourceName)!!.use { it.readBytes() }
        bytes.findString(strings)?.let { error("$it in $resourceName") }
    }
}
