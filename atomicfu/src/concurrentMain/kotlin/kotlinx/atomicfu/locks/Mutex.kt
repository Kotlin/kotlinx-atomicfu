package kotlinx.atomicfu.locks

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Mutex implementation for Kotlin/Native.
 * In concurrentMain sourceSet to be testable with Lincheck.
 * [park] and [unpark] functions can be passed for testability.
 */
internal class NativeMutex(
    val park : (Duration) -> Unit = { ParkingSupport.park(it) },
    val unpark : (ParkingHandle) -> Unit = ParkingSupport::unpark,
) {
    /**
     * The [state] variable stands for: 0 -> Lock is free
     *                                  1 -> Lock is locked but no waiters
     *                                  4 -> Lock is locked with 3 waiters
     *
     * The state.incrementAndGet() call makes my claim on the lock.
     * The returned value either means I acquired it (when it is 1).
     * Or I need to enqueue and park (when it is > 1).
     *
     * The [holdCount] variable is to enable reentrancy.
     *
     * Works by using a [parkingQueue].
     * When a thread tries to acquire the lock, but finds it is already locked it enqueues by appending to the [parkingQueue].
     * On enqueue the parking queue provides the second last node, this node is used to park on.
     * When our thread is woken up that means that the thread parked on the thrid last node called unpark on the second last node.
     * Since a woken up thread is first inline it means that it's node is the head and can therefore dequeue.
     *
     * Unlocking happens by calling state.decrementAndGet().
     * When the returned value is 0 it means the lock is free and we can simply return.
     * If the new state is > 0, then there are waiters. We wake up the first by unparking the head of the queue.
     * This even works when a thread is not parked yet,
     * since the ThreadParker can be pre-unparked resulting in the parking call to return immediately.
     */
    private val parkingQueue = ParkingQueue()
    private val owningThread = atomic<ParkingHandle?>(null)
    private val state = atomic(0)
    private val holdCount = atomic(0)
    
    fun lock() {
        tryLock(Duration.INFINITE)
    }

    fun tryLock(duration: Duration): Boolean {
        val currentParkingHandle = ParkingSupport.currentThreadHandle()

        // Has to be checked in this order!
        if (holdCount.value > 0 && currentParkingHandle == owningThread.value) {
            // Is reentring thread 
            holdCount.incrementAndGet()
            return true
        }

        // Otherwise try acquire lock
        val newState = state.incrementAndGet()
        // If new state 1 than I have acquired lock skipping queue.
        if (newState == 1) {
            owningThread.value = currentParkingHandle
            holdCount.incrementAndGet()
            return true
        }

        // If state larger than 1 -> enqueue and park
        // When woken up thread has acquired lock and his node in the queue is therefore at the head.
        // Remove head
        if (newState > 1) {
            val prevNode = parkingQueue.enqueue()
            // if timeout
            if (!prevNode.nodeWait(duration)) return false
            parkingQueue.dequeue()
            owningThread.value = currentParkingHandle
            holdCount.incrementAndGet()
            return true
        }
        
        return true
    }

    fun unlock() {
        val currentThreadId = ParkingSupport.currentThreadHandle()
        val currentOwnerId = owningThread.value
        if (currentThreadId != currentOwnerId) throw IllegalStateException("Thread is not holding the lock")

        // dec hold count
        val newHoldCount = holdCount.decrementAndGet()
        if (newHoldCount > 0) return
        if (newHoldCount < 0) throw IllegalStateException("Thread unlocked more than it locked")

        // Lock is released by decrementing (only if decremented to 0)
        val currentState = state.decrementAndGet()
        if (currentState == 0) return

        // If waiters wake up the first in line. The woken up thread will dequeue the node.
        if (currentState > 0) {
            var nextParker = parkingQueue.getHead()
            // If cancelled And there are other waiting nodes, go to next
            while (!nextParker.nodeWake() && state.decrementAndGet() > 0) {
                // We only dequeue here in case of timeoud out node.
                // Dequeueing woken nodes can lead to issues when pre-unparked.
                parkingQueue.dequeue()
                nextParker = parkingQueue.getHead()
            }
            return
        }
    }

    fun tryLock(): Boolean {
        val currentThreadId = ParkingSupport.currentThreadHandle()
        if (holdCount.value > 0 && owningThread.value == currentThreadId || state.compareAndSet(0, 1)) {
            owningThread.value = currentThreadId
            holdCount.incrementAndGet()
            return true
        }
        return false
    }

    // Based on Micheal-Scott Queue
    inner class ParkingQueue {
        private val head: AtomicRef<Node>
        private val tail: AtomicRef<Node>

        init {
            val first = Node()
            head = atomic(first)
            tail = atomic(first)
        }

        fun getHead(): Node {
            return head.value
        }

        fun enqueue(): Node {
            while (true) {
                val node = Node()
                val curTail = tail.value
                if (curTail.next.compareAndSet(null, node)) {
                    tail.compareAndSet(curTail, node)
                    return curTail
                }
                else tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }

        fun dequeue() {
            while (true) {
                val currentHead = head.value
                val currentHeadNext = currentHead.next.value ?: throw IllegalStateException("Dequeing parker but already empty, should not be possible")
                if (head.compareAndSet(currentHead, currentHeadNext)) return
            }
        }

    }

    inner class Node {
        val parker = atomic<Any>(Empty)
        val next = atomic<Node?>(null)
        
        fun nodeWait(duration: Duration): Boolean {
            val deadline = TimeSource.Monotonic.markNow() + duration
            while (true) {
                when (parker.value) {
                    Empty -> if (parker.compareAndSet(Empty, ParkingSupport.currentThreadHandle())) {
                        park(deadline - TimeSource.Monotonic.markNow())
                        if (deadline < TimeSource.Monotonic.markNow()) 
                            parker.compareAndSet(ParkingSupport.currentThreadHandle(), Cancelled)
                    }
                    is ParkingHandle -> {
                        park(deadline - TimeSource.Monotonic.markNow())
                        if (deadline < TimeSource.Monotonic.markNow())
                            parker.compareAndSet(ParkingSupport.currentThreadHandle(), Cancelled)
                    }
                    Awoken -> return true
                    Cancelled -> return false
                }
            }
        }
        
        fun nodeWake(): Boolean {
            while (true) {
                when (val currentState = parker.value) {
                    Empty -> if (parker.compareAndSet(Empty, Awoken)) return true
                    is ParkingHandle -> if (parker.compareAndSet(currentState, Awoken)) {
                        unpark(currentState)
                        return true
                    }
                    Awoken -> throw IllegalStateException("Node is already woken")
                    Cancelled -> return false
                }
            }
        }
    }
    
    private object Empty 
    private object Awoken
    private object Cancelled
}
