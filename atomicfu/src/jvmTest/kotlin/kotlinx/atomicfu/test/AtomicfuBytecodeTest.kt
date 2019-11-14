package kotlinx.atomicfu.test

import bytecode_test.*
import org.junit.*

/**
 * Makes sure classes do not have bytecode reference to atomicfu.
 */
class AtomicfuBytecodeTest {
    private val strings = listOf("kotlinx/atomicfu")

    /**
     * Test [SynchronizedObjectTest].
     */
    @Test
    fun testSynchronizedObjectBytecode() = checkBytecode(SynchronizedObjectTest::class.java)

    /**
     * Test [AtomicFieldTest].
     */
    @Test
    fun testAtomicFieldBytecode() = checkBytecode(AtomicFieldTest::class.java)

    /**
     * Test [ReentrantLockTest].
     */
    @Test
    fun testReentrantLockBytecode() = checkBytecode(ReentrantLockTest::class.java)

    private fun checkBytecode(javaClass: Class<*>) {
        val resourceName = javaClass.name.replace('.', '/') + ".class"
        val bytes = javaClass.classLoader.getResourceAsStream(resourceName)!!.use { it.readBytes() }
        bytes.findString(strings)?.let { error("$it in $resourceName") }
    }
}

