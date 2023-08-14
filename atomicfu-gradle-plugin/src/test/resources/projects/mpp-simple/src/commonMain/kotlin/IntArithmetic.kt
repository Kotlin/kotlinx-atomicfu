/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.atomicfu.*

class IntArithmetic {
    private val _x = atomic(0)
    val x get() = _x.value

    fun doWork() {
        _x.getAndSet(3)
        _x.compareAndSet(3, 8)
    }
}

// minimal example that forces ASM to call AtomicFUTransformer.CW.getCommonSuperClass
private fun checkTransformerFindCommonSuperClass() {
    val (a, b) = 0 to 1
    if (a == 0) {
        val c = listOf(a, b)
    }
}
