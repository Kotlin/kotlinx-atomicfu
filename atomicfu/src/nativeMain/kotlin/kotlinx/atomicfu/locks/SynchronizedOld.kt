package kotlinx.atomicfu.locks

import platform.posix.*
import kotlinx.atomicfu.locks.SynchronizedObjectOld.Status.*
import kotlinx.cinterop.UnsafeNumber
import kotlin.concurrent.AtomicReference

@OptIn(UnsafeNumber::class) // required for KT-60572
public open class SynchronizedObjectOld {

    protected val lock = AtomicReference(LockState(UNLOCKED, 0, 0))

    public fun lock() {
        val currentThreadId = pthread_self()!!
        while (true) {
            val state = lock.value
            when (state.status) {
                UNLOCKED -> {
                    val thinLock = LockState(THIN, 1, 0, currentThreadId)
                    if (lock.compareAndSet(state, thinLock))
                        return
                }

                THIN -> {
                    if (currentThreadId == state.ownerThreadId) {
                        // reentrant lock
                        val thinNested = LockState(THIN, state.nestedLocks + 1, state.waiters, currentThreadId)
                        if (lock.compareAndSet(state, thinNested))
                            return
                    } else {
                        // another thread is trying to take this lock -> allocate native mutex
                        val mutex = mutexPool.allocate()
                        mutex.lock()
                        val fatLock = LockState(FAT, state.nestedLocks, state.waiters + 1, state.ownerThreadId, mutex)
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

                FAT -> {
                    if (currentThreadId == state.ownerThreadId) {
                        // reentrant lock
                        val nestedFatLock =
                            LockState(FAT, state.nestedLocks + 1, state.waiters, state.ownerThreadId, state.mutex)
                        if (lock.compareAndSet(state, nestedFatLock)) return
                    } else if (state.ownerThreadId != null) {
                        val fatLock =
                            LockState(FAT, state.nestedLocks, state.waiters + 1, state.ownerThreadId, state.mutex)
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
            if (state.status == UNLOCKED) {
                val thinLock = LockState(THIN, 1, 0, currentThreadId)
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
                THIN -> {
                    // nested unlock
                    if (state.nestedLocks == 1) {
                        val unlocked = LockState(UNLOCKED, 0, 0)
                        if (lock.compareAndSet(state, unlocked))
                            return
                    } else {
                        val releasedNestedLock =
                            LockState(THIN, state.nestedLocks - 1, state.waiters, state.ownerThreadId)
                        if (lock.compareAndSet(state, releasedNestedLock))
                            return
                    }
                }

                FAT -> {
                    if (state.nestedLocks == 1) {
                        // last nested unlock -> release completely, resume some waiter
                        val releasedLock = LockState(FAT, 0, state.waiters - 1, null, state.mutex)
                        if (lock.compareAndSet(state, releasedLock)) {
                            releasedLock.mutex!!.unlock()
                            return
                        }
                    } else {
                        // lock is still owned by the current thread
                        val releasedLock =
                            LockState(FAT, state.nestedLocks - 1, state.waiters, state.ownerThreadId, state.mutex)
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
                LockState(THIN, 1, 0, threadId)
            else
                LockState(FAT, 1, state.waiters, threadId, state.mutex)
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

    protected enum class Status { UNLOCKED, THIN, FAT }
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