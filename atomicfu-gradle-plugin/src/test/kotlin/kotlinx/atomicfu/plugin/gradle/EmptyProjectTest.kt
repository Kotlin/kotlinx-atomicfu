package kotlinx.atomicfu.plugin.gradle

import org.junit.Test

class EmptyProjectTest : BaseKotlinGradleTest() {
    @Test
    fun testEmpty() = project("empty") {
        build("build") {}
    }
}
