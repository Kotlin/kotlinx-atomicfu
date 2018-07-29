package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic

class A {
    //internal val k = 5
    internal val internalField = atomic(false)
    internal val xxx = atomic(5)
    internal val yyy = atomic(6L)
    val zzz = atomic(5L)
}