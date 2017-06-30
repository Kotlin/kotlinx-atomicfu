package kotlinx.atomicfu.test

import org.junit.Test

class LockFreeStackTest {
    @Test
    fun testPushPop() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.push("A")
        check(!s.isEmpty())
        check(s.pop() == "A")
        check(s.isEmpty())
    }

    @Test
    fun testClear() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.push("A")
        check(!s.isEmpty())
        s.clear()
        check(s.isEmpty())
    }
}