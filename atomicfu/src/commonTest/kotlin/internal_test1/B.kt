/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package internal_test1

import kotlinx.atomicfu.test.A
import kotlin.test.*

class B {
    @Test
    fun testInternal() {
        val a = A()
        a.internalField.lazySet(true)
        check(a.internalField.getAndSet(false))
        check(a.xxx.addAndGet(4) == 9)
        check(a.yyy.compareAndSet(638753975930025820, 3444))
        check(a.intArr[2].compareAndSet(0, 6))
    }
}

class D {
    val da = A()
}