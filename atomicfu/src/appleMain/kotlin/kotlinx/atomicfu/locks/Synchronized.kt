/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kotlinx.atomicfu.locks

import kotlin.native.ref.createCleaner
import kotlinx.atomicfu.*
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import platform.posix.pthread_get_qos_class_np
import platform.posix.pthread_override_qos_class_end_np
import platform.posix.pthread_override_qos_class_start_np
import platform.posix.pthread_override_t
import platform.posix.pthread_self
import platform.posix.qos_class_self

import kotlin.native.concurrent.ThreadLocal

private const val NO_OWNER = 0L
private const val UNSET = 0L

@ThreadLocal
internal var currentThreadId = UNSET

// Based on the compose-multiplatform-core implementation with added qos and the pool back-ported
// from the atomicfu implementation.
public actual open class SynchronizedObject {
    private val ownerThreadId: AtomicLong = atomic(NO_OWNER)
    private var reEnterCount: Int = 0
    private val threadsOnLock: AtomicInt = atomic(0)

    private val monitor: DonatingMonitor by lazy { DonatingMonitor() }


    public fun lock() {
        var self = currentThreadId
        if (self == UNSET) {
            currentThreadId = pthread_self().toLong()
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
            currentThreadId = pthread_self().toLong()
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
                monitor.waitWithDonation(ownerThreadId.value)
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

    private inline fun withMonitor(monitor: DonatingMonitor, block: () -> Unit) {
        monitor.nativeMutex.lock()
        return try {
            block()
        } finally {
            monitor.nativeMutex.unlock()
        }
    }

    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    private class DonatingMonitor {
        val nativeMutex = mutexPool.allocate()
        val cleaner = createCleaner(nativeMutex) { mutexPool.release(it) }
        var qosOverride: pthread_override_t? = null
        var qosOverrideQosClass: UInt = 0U

        fun waitWithDonation(lockOwner: Long) {
            donateQos(lockOwner)
            nativeMutex.wait()
            clearDonation()
        }

        private fun donateQos(lockOwner: Long) {
            if (lockOwner == NO_OWNER) {
                return
            }
            val ourQosClass = qos_class_self()
            // Set up a new override if required:
            if (qosOverride != null) {
                // There is an existing override, but we need to go higher.
                if (ourQosClass > qosOverrideQosClass) {
                    pthread_override_qos_class_end_np(qosOverride)
                    qosOverride = pthread_override_qos_class_start_np(lockOwner.toCPointer(), qos_class_self(), 0)
                    qosOverrideQosClass = ourQosClass
                }
            } else {
                // No existing override, check if we need to set one up.
                pthread_get_qos_class_np(lockOwner.toCPointer(), nativeMutex.lockOwnerQosClass.ptr, nativeMutex.lockOwnerRelPrio.ptr)
                if (ourQosClass > nativeMutex.lockOwnerQosClass.value) {
                    qosOverride = pthread_override_qos_class_start_np(lockOwner.toCPointer(), ourQosClass, 0)
                    qosOverrideQosClass = ourQosClass
                }
            }
        }

        private fun clearDonation() {
            if (qosOverride != null) {
                pthread_override_qos_class_end_np(qosOverride)
                qosOverride = null
            }
        }
    }
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


private const val INITIAL_POOL_CAPACITY = 64
private const val MAX_POOL_SIZE = 1024

private val mutexPool by lazy { MutexPool() }

private class MutexPool() {
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
