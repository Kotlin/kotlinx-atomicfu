import kotlin.test.*

class ArithmeticTest {
    @Test
    fun testInt() {
        val a = IntArithmetic()
        doWork(a)
        check(a.x == 8)
    }
}