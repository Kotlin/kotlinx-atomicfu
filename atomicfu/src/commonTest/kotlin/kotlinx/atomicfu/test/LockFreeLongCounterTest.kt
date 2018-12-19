/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

class LockFreeLongCounterTest {
    private inline fun testWith(g: LockFreeLongCounter.() -> Long) {
        val c = LockFreeLongCounter()
        check(c.g() == 0L)
        check(c.increment() == 1L)
        check(c.g() == 1L)
        check(c.increment() == 2L)
        check(c.g() == 2L)
    }

    @Test
    fun testBasic() = testWith { get() }

    @Test
    fun testGetInner() = testWith { getInner() }

    @Test
    fun testAdd2() {
        val c = LockFreeLongCounter()
        c.add2()
        check(c.get() == 2L)
        c.add2()
        check(c.get() == 4L)
    }

    @Test
    fun testSetM2() {
        val c = LockFreeLongCounter()
        c.setM2()
        check(c.get() == -2L)
    }
}

class LockFreeLongCounter {
    private val counter = atomic(0L)

    fun get(): Long = counter.value

    fun increment(): Long = counter.incrementAndGet()

    fun add2() = counter.getAndAdd(2)

    fun setM2() {
        counter.value = -2L // LDC instruction here
    }

    fun getInner(): Long = Inner().getFromOuter()

    // testing how an inner class can get access to it
    private inner class Inner {
        fun getFromOuter(): Long = counter.value
    }
}