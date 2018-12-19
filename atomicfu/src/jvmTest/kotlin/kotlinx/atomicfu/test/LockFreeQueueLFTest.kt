/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
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