package kotlinx.atomicfu.test

import org.junit.Test

class LockFreeLongCounterTest {
    @Test
    fun testBasic() {
        val c = LockFreeLongCounter()
        check(c.get() == 0L)
        check(c.increment() == 1L)
        check(c.get() == 1L)
        check(c.increment() == 2L)
        check(c.get() == 2L)
    }
}