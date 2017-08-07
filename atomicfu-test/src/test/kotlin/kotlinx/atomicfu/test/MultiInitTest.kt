package kotlinx.atomicfu.test

import org.junit.Test

class MultiInitTest {
    @Test
    fun testBasic() {
        val t = MultiInit()
        check(t.incA() == 1)
        check(t.incA() == 2)
        check(t.incB() == 1)
        check(t.incB() == 2)
    }
}