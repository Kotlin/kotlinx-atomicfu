package kotlinx.atomicfu.test

import org.junit.Test
import java.io.File

private const val ATOMICFU_PREFIX = "atomicfu\$"
private const val KOTLINX_ATOMICFU_MODULE = "\$module\$kotlinx_atomicfu"

/**
 * Makes sure transformed js output does not have references to atomicfu.
 */
class AtomicfuReferenceJsTest {

    private val TRANSFORMED_JS_FILE = System.getenv("transformedJsFile")
    private val dependencies = listOf(ATOMICFU_PREFIX, KOTLINX_ATOMICFU_MODULE)

    @Test
    fun testAtomicfuDependencies() {
        val bytes = File(TRANSFORMED_JS_FILE).inputStream().use { it.readBytes() }
        bytes.findString(dependencies)?.let { error("$it in $TRANSFORMED_JS_FILE") }
    }
}