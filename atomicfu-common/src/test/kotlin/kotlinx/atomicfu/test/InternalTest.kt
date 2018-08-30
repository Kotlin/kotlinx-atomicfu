package kotlinx.atomicfu.test

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic

class A {
    internal val internalField = atomic(false)
    internal val xxx = atomic(5)
    internal val yyy = atomic(638753975930025820)
    internal val zzz = atomic(Node(5))
    internal val arr = AtomicIntArray(5)
}

class Node(val value: Int) {
    val next = atomic<Node?>(null)
}