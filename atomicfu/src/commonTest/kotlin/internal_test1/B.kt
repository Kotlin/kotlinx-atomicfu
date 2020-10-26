/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package internal_test1

import bytecode_test.c
import kotlinx.atomicfu.test.A
import kotlinx.atomicfu.test.internalTopLevelField
import kotlinx.atomicfu.test.publicTopLevelField
import kotlin.test.*

class B {
    @Test
    fun testInternal() {
        val a = A()
        a.internalField.lazySet(true)
        assertEquals(true, a.internalField.value)
        check(a.internalField.getAndSet(false))
        assertEquals(false, a.internalField.value)
        check(a.xxx.addAndGet(4) == 9)
        assertEquals(9, a.xxx.value)
        check(a.yyy.compareAndSet(638753975930025820, 3444))
        assertEquals(3444, a.yyy.value)
        check(a.intArr[2].compareAndSet(0, 6))
        assertEquals(6, a.intArr[2].value)
        check(a.refArr[3].compareAndSet(null, "OK"))
        assertEquals("OK", a.refArr[3].value)
    }

    @Test
    fun testInternalTopLevel() {
        internalTopLevelField.lazySet(55)
        check(internalTopLevelField.value == 55)
        check(publicTopLevelField.compareAndSet(0, 66))
        check(publicTopLevelField.value == 66)
        check(c.getAndSet(145) == 0)
    }
}

class D {
    val da = A()
}