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

import kotlinx.atomicfu.LockFreedomTestEnvironment
import org.junit.Test
import java.util.*

class LockFreeQueueLFTest : LockFreedomTestEnvironment("LockFreeQueueLFTest") {
    val nEnqueuers = 2
    val nDequeuers = 2
    val nSeconds = 5

    val queue = LockFreeQueue()

    @Test
    fun testLockFreedom() {
        repeat(nEnqueuers) { id ->
            val rnd = Random()
            testThread("Enqueue-$id") {
                queue.enqueue(rnd.nextInt(1000))
            }
        }
        repeat(nDequeuers) { id ->
            testThread("Dequeue-$id") {
                queue.dequeue()
            }
        }
        performTest(nSeconds)
    }
}