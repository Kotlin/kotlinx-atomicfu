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
import kotlin.math.*

class ArithmeticTest {
    @Test
    fun testInt() {
        val a = IntArithmetic()
        check(a._x == 0)
        check(a.x.getAndSet(3) == 0)
        check(a.x.compareAndSet(3, 8))
        a.x.lazySet(1)
        check(a._x == 1)
        check(a.x.getAndSet(2) == 1)
        check(a._x == 2)
        check(a.x.getAndIncrement() == 2)
        check(a._x == 3)
        check(a.x.getAndDecrement() == 3)
        check(a._x == 2)
        check(a.x.getAndAdd(2) == 2)
        check(a._x == 4)
        check(a.x.addAndGet(3) == 7)
        check(a._x == 7)
        check(a.x.incrementAndGet() == 8)
        check(a._x == 8)
        check(a.x.decrementAndGet() == 7)
        check(a._x == 7)
        a.x.compareAndSet(7, 10)
    }

    @Test
    fun testLong() {
        val a = LongArithmetic()
        check(a.z.value == 2424920024888888848)
        a.z.lazySet(8424920024888888848)
        check(a.z.value == 8424920024888888848)
        check(a.z.getAndSet(8924920024888888848) == 8424920024888888848)
        check(a.z.value == 8924920024888888848)
        check(a.z.incrementAndGet() == 8924920024888888849)
        check(a.z.value == 8924920024888888849)
        check(a.z.getAndDecrement() == 8924920024888888849)
        check(a.z.value == 8924920024888888848)
        check(a.z.getAndAdd(100000000000000000) == 8924920024888888848)
        check(a.z.value == 9024920024888888848)
        check(a.z.addAndGet(-9223372036854775807) == -198452011965886959)
        check(a.z.value == -198452011965886959)
        check(a.z.incrementAndGet() == -198452011965886958)
        check(a.z.value == -198452011965886958)
        check(a.z.decrementAndGet() == -198452011965886959)
        check(a.z.value == -198452011965886959)
//        a.z.plusAssign(34000000000)
//        check(a.z.value == -198451977965886959)
//        a.z.minusAssign(1000000000000000000)
//        check(a.z.value == -1198451977965886959)
    }

    @Test
    fun testBoolean() {
        val a = BooleanArithmetic()
        check(!a.x)
        a._x.lazySet(true)
        check(a.x)
        check(a._x.getAndSet(true))
        check(a._x.compareAndSet(true, false))
        check(!a.x)
    }
}

class IntArithmetic {
    val x = atomic(0)
    val _x get() = x.value
    fun aaa() = x.addAndGet(7)
}

class LongArithmetic {
    val _x = atomic(4294967296)
    val y = atomic(5000000000)
    val z = atomic(2424920024888888848)
    val max = atomic(9223372036854775807)
    val x get() = _x.value
}

class BooleanArithmetic {
    val _x = atomic(false)
    val x get() = _x.value
}
