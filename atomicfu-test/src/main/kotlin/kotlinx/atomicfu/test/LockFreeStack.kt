package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

class LockFreeStack<T> {
    private val top = atomic<Node<T>>()

    private class Node<T>(val value: T, val next: Node<T>?)

    fun isEmpty() = top.value == null

    fun clear() { top.value = null }

    fun push(value: T) {
        top.loop { cur ->
            val upd = Node(value, cur)
            if (top.compareAndSet(cur, upd)) return
        }
    }

    fun pop(): T? {
        top.loop { cur ->
            if (cur == null) return null
            if (top.compareAndSet(cur, cur.next)) return cur.value
        }
    }
}
