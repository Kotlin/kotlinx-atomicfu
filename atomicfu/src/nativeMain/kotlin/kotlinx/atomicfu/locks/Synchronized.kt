package kotlinx.atomicfu.locks

import kotlinx.cinterop.UnsafeNumber
import platform.posix.pthread_self
import platform.posix.pthread_t
import kotlin.concurrent.AtomicReference

@OptIn(UnsafeNumber::class) // required for KT-60572
public actual open class SynchronizedObject {
    
    private val nativeMutex: NativeMutex = NativeMutex { kotlinx.atomicfu.parking.PosixParkingDelegator }
    public fun lock() = nativeMutex.lock()
    public fun tryLock(): Boolean = nativeMutex.tryLock()
    public fun unlock() = nativeMutex.unlock()
    
//    private val oldLock: OldLock = OldLock()
//    public fun lock() = oldLock.lock()
//    public fun unlock() = oldLock.unlock()
//    public fun tryLock() = oldLock.tryLock()

}

public actual fun reentrantLock() = ReentrantLock()

public actual typealias ReentrantLock = SynchronizedObject

public actual inline fun <T> ReentrantLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T {
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}

private class OldLock {
    protected enum class Status { UNLOCKED, THIN, FAT }
    protected val lock = AtomicReference(LockState(Status.UNLOCKED, 0, 0))

    public fun lock() {
        val currentThreadId = pthread_self()!!
        while (true) {
            val state = lock.value
            when (state.status) {
                Status.UNLOCKED -> {
                    val thinLock = LockState(Status.THIN, 1, 0, currentThreadId)
                    if (lock.compareAndSet(state, thinLock))
                        return
                }

                Status.THIN -> {
                    if (currentThreadId == state.ownerThreadId) {
                        // reentrant lock
                        val thinNested = LockState(Status.THIN, state.nestedLocks + 1, state.waiters, currentThreadId)
                        if (lock.compareAndSet(state, thinNested))
                            return
                    } else {
                        // another thread is trying to take this lock -> allocate native mutex
                        val mutex = mutexPool.allocate()
                        mutex.lock()
                        val fatLock = LockState(Status.FAT, state.nestedLocks, state.waiters + 1, state.ownerThreadId, mutex)
                        if (lock.compareAndSet(state, fatLock)) {
                            //block the current thread waiting for the owner thread to release the permit
                            mutex.lock()
                            tryLockAfterResume(currentThreadId)
                            return
                        } else {
                            // return permit taken for the owner thread and release mutex back to the pool
                            mutex.unlock()
                            mutexPool.release(mutex)
                        }
                    }
                }

                Status.FAT -> {
                    if (currentThreadId == state.ownerThreadId) {
                        // reentrant lock
                        val nestedFatLock =
                            LockState(Status.FAT, state.nestedLocks + 1, state.waiters, state.ownerThreadId, state.mutex)
                        if (lock.compareAndSet(state, nestedFatLock)) return
                    } else if (state.ownerThreadId != null) {
                        val fatLock =
                            LockState(Status.FAT, state.nestedLocks, state.waiters + 1, state.ownerThreadId, state.mutex)
                        if (lock.compareAndSet(state, fatLock)) {
                            fatLock.mutex!!.lock()
                            tryLockAfterResume(currentThreadId)
                            return
                        }
                    }
                }
            }
        }
    }

    public fun tryLock(): Boolean {
        val currentThreadId = pthread_self()!!
        while (true) {
            val state = lock.value
            if (state.status == Status.UNLOCKED) {
                val thinLock = LockState(Status.THIN, 1, 0, currentThreadId)
                if (lock.compareAndSet(state, thinLock))
                    return true
            } else {
                if (currentThreadId == state.ownerThreadId) {
                    val nestedLock =
                        LockState(state.status, state.nestedLocks + 1, state.waiters, currentThreadId, state.mutex)
                    if (lock.compareAndSet(state, nestedLock))
                        return true
                } else {
                    return false
                }
            }
        }
    }

    public fun unlock() {
        val currentThreadId = pthread_self()!!
        while (true) {
            val state = lock.value
            require(currentThreadId == state.ownerThreadId) { "Thin lock may be only released by the owner thread, expected: ${state.ownerThreadId}, real: $currentThreadId" }
            when (state.status) {
                Status.THIN -> {
                    // nested unlock
                    if (state.nestedLocks == 1) {
                        val unlocked = LockState(Status.UNLOCKED, 0, 0)
                        if (lock.compareAndSet(state, unlocked))
                            return
                    } else {
                        val releasedNestedLock =
                            LockState(Status.THIN, state.nestedLocks - 1, state.waiters, state.ownerThreadId)
                        if (lock.compareAndSet(state, releasedNestedLock))
                            return
                    }
                }

                Status.FAT -> {
                    if (state.nestedLocks == 1) {
                        // last nested unlock -> release completely, resume some waiter
                        val releasedLock = LockState(Status.FAT, 0, state.waiters - 1, null, state.mutex)
                        if (lock.compareAndSet(state, releasedLock)) {
                            releasedLock.mutex!!.unlock()
                            return
                        }
                    } else {
                        // lock is still owned by the current thread
                        val releasedLock =
                            LockState(Status.FAT, state.nestedLocks - 1, state.waiters, state.ownerThreadId, state.mutex)
                        if (lock.compareAndSet(state, releasedLock))
                            return
                    }
                }

                else -> error("It is not possible to unlock the mutex that is not obtained")
            }
        }
    }

    private fun tryLockAfterResume(threadId: pthread_t) {
        while (true) {
            val state = lock.value
            val newState = if (state.waiters == 0) // deflate
                LockState(Status.THIN, 1, 0, threadId)
            else
                LockState(Status.FAT, 1, state.waiters, threadId, state.mutex)
            if (lock.compareAndSet(state, newState)) {
                if (state.waiters == 0) {
                    state.mutex!!.unlock()
                    mutexPool.release(state.mutex)
                }
                return
            }
        }
    }

    protected class LockState(
        val status: Status,
        val nestedLocks: Int,
        val waiters: Int,
        val ownerThreadId: pthread_t? = null,
        val mutex: NativeMutexNode? = null
    )

}

private const val INITIAL_POOL_CAPACITY = 64

private val mutexPool by lazy { MutexPool(INITIAL_POOL_CAPACITY) }

class MutexPool(capacity: Int) {
    private val top = AtomicReference<NativeMutexNode?>(null)

    private val mutexes = Array(capacity) { NativeMutexNode() }

    init {
        // Immediately form a stack
        for (mutex in mutexes) {
            release(mutex)
        }
    }

    private fun allocMutexNode() = NativeMutexNode()

    fun allocate(): NativeMutexNode = pop() ?: allocMutexNode()

    fun release(mutexNode: NativeMutexNode) {
        while (true) {
            val oldTop = top.value
            mutexNode.next = oldTop
            if (top.compareAndSet(oldTop, mutexNode)) {
                return
            }
        }
    }

    private fun pop(): NativeMutexNode? {
        while (true) {
            val oldTop = top.value
            if (oldTop == null)
                return null
            val newHead = oldTop.next
            if (top.compareAndSet(oldTop, newHead)) {
                return oldTop
            }
        }
    }
}
