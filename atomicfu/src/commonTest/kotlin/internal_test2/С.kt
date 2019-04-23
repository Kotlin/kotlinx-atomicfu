/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package internal_test2

import internal_test1.D
import kotlinx.atomicfu.test.A
import kotlin.test.*

class C {
    @Test
    fun testInternal() {
        val a = A()
        check(a.yyy.decrementAndGet() == 638753975930025819)
        check(a.intArr[3].getAndAdd(5) == 0)
        val d = D()
        check(d.da.intArr[2].compareAndSet(0, 38535))
        check(d.da.xxx.getAndAdd(90) == 5)
        check(d.da.xxx.value == 95)
    }

    @Test
    fun testInternalGetField() {
        val a = A()
        a.set(1, "Hello")
    }
}