/*
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    fun add2() {
        counter += 2
    }

    fun setM2() {
        counter.value = -2L // LDC instruction here
    }

    fun getInner(): Long = Inner().getFromOuter()

    // testing how an inner class can get access to it
    private inner class Inner {
        fun getFromOuter(): Long = counter.value
    }
}