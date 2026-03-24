/*
 * Copyright 2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package kotlinx.atomicfu.locks

import kotlin.native.ref.createCleaner
import kotlinx.atomicfu.*
import kotlin.experimental.ExperimentalNativeApi


import kotlin.native.concurrent.ThreadLocal

internal const val NO_OWNER = 0L
private const val UNSET = 0L

@ThreadLocal
private var currentThreadId = UNSET

// Based on the compose-multiplatform-core implementation with added qos and the pool back-ported
// from the atomicfu implementation.
public actual open class SynchronizedObject {
    private val ownerThreadId: AtomicLong = atomic(NO_OWNER)
    private var reEnterCount: Int = 0
    private val threadsOnLock: AtomicInt = atomic(0)

    private val monitor: MonitorWrapper by lazy { MonitorWrapper() }


    public fun lock() {
        var self = currentThreadId
        if (self == UNSET) {
            currentThreadId = createThreadId()
            self = currentThreadId
        }
        if (ownerThreadId.value == self) {
            reEnterCount += 1
        } else if (threadsOnLock.incrementAndGet() > 1) {
            waitForUnlockAndLock(self)
        } else {
            if (!ownerThreadId.compareAndSet(NO_OWNER, self)) {
                waitForUnlockAndLock(self)
            }
        }
    }

    public fun tryLock(): Boolean {
        var self = currentThreadId
        if (self == 0L) {
            currentThreadId = createThreadId()
            self = currentThreadId
        }
        return if (ownerThreadId.value == self) {
            reEnterCount += 1
            true
        } else if (threadsOnLock.incrementAndGet() == 1 && ownerThreadId.compareAndSet(NO_OWNER, self)) {
            true
        } else {
            threadsOnLock.decrementAndGet()
            false
        }
    }


    private fun waitForUnlockAndLock(self: Long) {
        withMonitor(monitor) {
            while (!ownerThreadId.compareAndSet(NO_OWNER, self)) {
                monitor.nativeMutex.wait(ownerThreadId.value)
            }
        }
    }

    public fun unlock() {
        require (ownerThreadId.value == currentThreadId)
        if (reEnterCount > 0) {
            reEnterCount -= 1
        } else {
            ownerThreadId.value = NO_OWNER
            if (threadsOnLock.decrementAndGet() > 0) {
                withMonitor(monitor) {
                    // We expect the highest priority thread to be woken up, but this should work
                    // in any case.
                    monitor.nativeMutex.notify()
                }
            }
        }
    }

    private inline fun withMonitor(monitor: MonitorWrapper, block: () -> Unit) {
        monitor.nativeMutex.lock()
        return try {
            block()
        } finally {
            monitor.nativeMutex.unlock()
        }
    }

    @OptIn(ExperimentalNativeApi::class)
    private class MonitorWrapper {
        val nativeMutex = mutexPool.allocate()
        val cleaner = createCleaner(nativeMutex) { mutexPool.release(it) }
    }
}


public actual fun reentrantLock(): ReentrantLock = ReentrantLock()

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


private const val INITIAL_POOL_CAPACITY = 64
private const val MAX_POOL_SIZE = 1024

internal val mutexPool by lazy { MutexPool() }

internal class MutexPool() {
    private val size = atomic(0)
    private val top = atomic<NativeMutexNode?>(null)

    init {
        // Immediately form a stack
        for (i in 0 until INITIAL_POOL_CAPACITY) {
            release(NativeMutexNode())
        }
    }

    private fun allocMutexNode() = NativeMutexNode()

    fun allocate(): NativeMutexNode = pop() ?: allocMutexNode()

    fun release(mutexNode: NativeMutexNode) {
        if (size.value > MAX_POOL_SIZE) {
            mutexNode.dispose()
        } else {
            while (true) {
                val oldTop = top.value
                mutexNode.next = oldTop
                if (top.compareAndSet(oldTop, mutexNode)) {
                    size.incrementAndGet()
                    return
                }
            }
        }
    }

    private fun pop(): NativeMutexNode? {
        while (true) {
            val oldTop = top.value
            if (oldTop == null) {
                return null
            }
            val newHead = oldTop.next
            if (top.compareAndSet(oldTop, newHead)) {
                size.decrementAndGet()
                return oldTop
            }
        }
    }
}
