package kotlinx.atomicfu.test

import org.junit.Test

class LockFreeStackTest {
    @Test
    fun testClear() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushLoop("A")
        check(!s.isEmpty())
        s.clear()
        check(s.isEmpty())
    }

    @Test
    fun testPushPopLoop() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushLoop("A")
        check(!s.isEmpty())
        check(s.popLoop() == "A")
        check(s.isEmpty())
    }

    @Test
    fun testPushPopUpdate() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushUpdate("A")
        check(!s.isEmpty())
        check(s.popUpdate() == "A")
        check(s.isEmpty())
    }
}
