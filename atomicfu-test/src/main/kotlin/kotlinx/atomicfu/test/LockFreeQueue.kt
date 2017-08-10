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

@file:Suppress("RedundantVisibilityModifier")

package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

// MS-queue
public class LockFreeQueue {
    private val head = atomic(Node(0))
    private val tail = atomic(head.value)

    private class Node(val value: Int) {
        val next = atomic<Node?>(null)
    }

    public fun enqueue(value: Int) {
        val node = Node(value)
        tail.loop { curTail ->
            val curNext = curTail.next.value
            if (curNext != null) {
                tail.compareAndSet(curTail, curNext)
                return@loop
            }
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            }
        }
    }

    public fun dequeue(): Int {
        head.loop { curHead ->
            val next = curHead.next.value ?: return -1
            if (head.compareAndSet(curHead, next)) return next.value
        }
    }
}