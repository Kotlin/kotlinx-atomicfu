/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class A {
    internal val internalField = atomic(false)
    internal val xxx = atomic(5)
    internal val yyy = atomic(638753975930025820)
    internal val zzz = atomic(Node(5))

    internal val intArr = AtomicIntArray(5)
    internal val refArr = atomicArrayOfNulls<String>(10)

    fun set(index: Int, data: String) = refArr[index].compareAndSet(null, data)
}

class Node(val value: Int) {
    val next = atomic<Node?>(null)
}