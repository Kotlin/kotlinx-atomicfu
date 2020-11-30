package kotlinx.atomicfu.test

import bytecode_test.*
import org.junit.*

private const val KOTLINX_ATOMICFU = "kotlinx/atomicfu"
private const val KOTLIN_REFLECTION = "kotlin/reflect"

/**
 * Makes sure classes do not have bytecode reference to atomicfu.
 */
class AtomicfuBytecodeTest {
    /**
     * Test [SynchronizedObjectTest].
     */
    @Test
    fun testSynchronizedObjectBytecode() = checkBytecode(SynchronizedObjectTest::class.java, listOf(KOTLINX_ATOMICFU))

    /**
     * Test [AtomicFieldTest].
     */
    @Test
    fun testAtomicFieldBytecode() = checkBytecode(AtomicFieldTest::class.java, listOf(KOTLINX_ATOMICFU))

    /**
     * Test [ReentrantLockTest].
     */
    @Test
    fun testReentrantLockBytecode() = checkBytecode(ReentrantLockTest::class.java, listOf(KOTLINX_ATOMICFU))

    /**
     * Test [TraceUseTest].
     */
    @Test
    fun testTraceUseBytecode() = checkBytecode(TraceUseTest::class.java, listOf(KOTLINX_ATOMICFU))

    /**
     * Test [DelegatedProperties].
     */
    @Test
    fun testDelegatedPropertiesBytecode() = checkBytecode(DelegatedProperties::class.java, listOf(KOTLIN_REFLECTION))

    private fun checkBytecode(javaClass: Class<*>, strings: List<String>) {
        val resourceName = javaClass.name.replace('.', '/') + ".class"
        val bytes = javaClass.classLoader.getResourceAsStream(resourceName)!!.use { it.readBytes() }
        bytes.findString(strings)?.let { error("$it in $resourceName") }
    }
}
