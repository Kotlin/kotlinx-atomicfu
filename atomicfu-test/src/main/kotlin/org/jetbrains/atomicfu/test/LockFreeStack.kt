package org.jetbrains.atomicfu.test

import java.util.concurrent.atomic.AtomicReference

class LockFreeStack<T> {
    private val top = AtomicReference<Node<T>?>(null)

    private class Node<T>(val value: T, val next: Node<T>?)

    fun push(value: T) {
        while (true) {
            val cur = top.get()
            val upd = Node(value, cur)
            if (top.compareAndSet(cur, upd)) break
        }
    }

    fun pop(): T? {
        while (true) {
            val cur = top.get() ?: return null
            if (top.compareAndSet(cur, cur.next)) return cur.value
        }
    }
}
