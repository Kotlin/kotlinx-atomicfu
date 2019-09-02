package kotlinx.atomicfu.test

import org.junit.*

/**
 * Makes sure that [VolatileOnlyTest] does not have FU/VH usages in bytecode
 */
class VolatileOnlyBytecodeTest {
    private val strings = listOf("FieldUpdater", "VarHandle")

    @Test
    fun testBytecode() {
        val javaClass = VolatileOnlyTest::class.java
        val resourceName = javaClass.name.replace('.', '/') + ".class"
        val bytes = javaClass.classLoader.getResourceAsStream(resourceName)!!.use { it.readBytes() }
        for (ss in strings) {
            val bs = ss.toByteArray()
            for (i in bytes.indices) {
                require(!bytes.equalsAt(i, bs)) {
                    error("Found unexpected string $ss in $resourceName at offset $i")
                }
            }
        }
    }

    private fun ByteArray.equalsAt(i: Int, bs: ByteArray): Boolean {
        if (i + bs.size >= size) return false
        for (k in bs.indices) {
            if (this[i + k] != bs[k]) return false
        }
        return true
    }
}

