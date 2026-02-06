package kotlinx.atomicfu.locks

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration

private const val TEST_ITERATIONS = 5
private const val MAX_RANDOM_WAIT_MILLIS = 5L
private val THREAD_SETS = listOf(2, 5, 7)

class LatchTest {
    private class Arrs(numberOfThreads: Int) {
        val after = AtomicIntArray(numberOfThreads)
        val before = AtomicIntArray(numberOfThreads)

        init {
            repeat(numberOfThreads) {
                after[it].value = 0
                before[it].value = 0
            }
        }
    }

    @Test
    fun latchTest() {
        repeat(TEST_ITERATIONS) { iteration ->
            THREAD_SETS.forEach { numberOfThreads ->
                val countingDownTo = iteration + 2
                val ar = Arrs(numberOfThreads)
                val latch = CustomCountDownLatch(countingDownTo)
                val countingThread = Fut {
                    repeat(countingDownTo) {
                        sleepMillis(Random.nextLong(MAX_RANDOM_WAIT_MILLIS))

                        repeat(ar.after.size) { threadToCheck ->
                            if (ar.after[threadToCheck].value != 0) fail("Thread passed latch too early")
                        }

                        latch.countDown()
                    }
                }

                val waiters = List(numberOfThreads) { i ->
                    Fut {
                        sleepMillis(Random.nextLong(MAX_RANDOM_WAIT_MILLIS))
                        latch.await()
                        ar.after[i].value = 1
                    }
                }

                Fut.waitAllAndThrow(waiters + countingThread)

                repeat(ar.after.size) { threadToCheck ->
                    if (ar.after[threadToCheck].value != 1) fail("Thread $threadToCheck stuck")
                }
            }
        }
    }
}

class CustomCountDownLatch(count: Int) {
    private val c = atomic(count)
    private val waiters = MpscStack()

    fun await() {
        val handle = ParkingSupport.currentThreadHandle()
        if (waiters.push(handle)) {
            // Each pushed thread must be parked at least once.
            // Unpark before park makes the next park return immediately.
            // Unpark will be definitely called on each pushed thread in `drain`.
            // So we must counteract the side effects of this unpark.
            do {
                ParkingSupport.park(Duration.INFINITE)
            } while (c.value > 0)
        }
    }

    fun countDown() {
        val count = c.decrementAndGet()
        if (count > 0) return
        if (count < 0) error("Count down to negative value: $count is prohibited.")
        // count == 0, c <= 0
        waiters.drain {
            ParkingSupport.unpark(it)
        }
    }
}

private class MpscStack {
    // Invariant: node only stores non-null values. Node(null, _) is a sentinel.
    private class Node(val data: ParkingHandle?, var next: Node?)

    companion object {
        private val sentinel = Node(null, null)
    }

    // Invariant: once head stores null, it will always store null.
    // Head storing null indicates the end of the stack usage. The stack is considered closed.
    // Any further push will return false. Any further drain will drain an empty list.
    private val head = atomic<Node?>(sentinel)

    fun push(element: ParkingHandle): Boolean {
        val node = Node(element, null)
        head.loop { cur ->
            if (cur == null) return false
            node.next = cur
            if (head.compareAndSet(cur, node)) return true
        }
    }

    fun drain(consumer: (ParkingHandle) -> Unit) {
        var node = head.getAndSet(null)
        while (node != null) {
            if (node == sentinel) break
            consumer(node.data!!)
            node = node.next
        }
    }
}
