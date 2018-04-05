/*
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
