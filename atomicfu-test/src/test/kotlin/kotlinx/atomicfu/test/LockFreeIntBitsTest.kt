package kotlinx.atomicfu.test

import org.junit.Test

class LockFreeIntBitsTest {
    @Test
    fun testBasic() {
        val bs = LockFreeIntBits()
        check(!bs[0])
        check(bs.bitSet(0))
        check(bs[0])
        check(!bs.bitSet(0))

        check(!bs[1])
        check(bs.bitSet(1))
        check(bs[1])
        check(!bs.bitSet(1))
        check(!bs.bitSet(0))

        check(bs[0])
        check(bs.bitClear(0))
        check(!bs.bitClear(0))

        check(bs[1])
    }
}