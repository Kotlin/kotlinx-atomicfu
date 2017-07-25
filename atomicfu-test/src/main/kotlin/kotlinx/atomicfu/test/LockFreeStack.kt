package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.loop
import kotlinx.atomicfu.update

class LockFreeStack<T> {
    private val top = atomic<Node<T>?>(null)

    private class Node<T>(val value: T, val next: Node<T>?)

    fun isEmpty() = top.value == null

    fun clear() { top.value = null }

    fun pushLoop(value: T) {
        top.loop { cur ->
            val upd = Node(value, cur)
            if (top.compareAndSet(cur, upd)) return
        }
    }

    fun popLoop(): T? {
        top.loop { cur ->
            if (cur == null) return null
            if (top.compareAndSet(cur, cur.next)) return cur.value
        }
    }

    fun pushUpdate(value: T) {
        top.update { cur -> Node(value, cur) }
    }

    fun popUpdate(): T? =
        top.getAndUpdate { cur -> cur?.next } ?.value
}
