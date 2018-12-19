/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

class LockFreeStackTest {
    @Test
    fun testClear() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushLoop("A")
        check(!s.isEmpty())
        s.clear()
        check(s.isEmpty())
    }

    @Test
    fun testPushPopLoop() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushLoop("A")
        check(!s.isEmpty())
        check(s.popLoop() == "A")
        check(s.isEmpty())
    }

    @Test
    fun testPushPopUpdate() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushUpdate("A")
        check(!s.isEmpty())
        check(s.popUpdate() == "A")
        check(s.isEmpty())
    }
}

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
