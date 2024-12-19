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
import kotlinx.cinterop.Arena
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import platform.posix.pthread_cond_destroy
import platform.posix.pthread_cond_init
import platform.posix.pthread_cond_signal
import platform.posix.pthread_cond_t
import platform.posix.pthread_cond_wait
import platform.posix.pthread_get_qos_class_np
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t
import platform.posix.pthread_override_qos_class_end_np
import platform.posix.pthread_override_qos_class_start_np
import platform.posix.pthread_override_t
import platform.posix.pthread_self
import platform.posix.qos_class_self

import platform.posix.PTHREAD_MUTEX_ERRORCHECK


@ThreadLocal
var currentThreadId = 0L

// Based on the compose-multiplatform-core implementation with added qos and the pool back-ported
// from the atomicfu implementation.
public actual open class SynchronizedObject {

    companion object {
        private const val NO_OWNER = 0L
    }

    private val owner: AtomicLong = atomic(NO_OWNER)
    private var reEnterCount: Int = 0
    private val waiters: AtomicInt = atomic(0)

    private val monitor: DonatingMonitor by lazy { DonatingMonitor() }


    public fun lock() {
        var self = currentThreadId
        if (self == 0L) {
            currentThreadId = pthread_self().toLong()
            self = currentThreadId
        }
        if (owner.value == self) {
            reEnterCount += 1
        } else if (waiters.incrementAndGet() > 1) {
            waitForUnlockAndLock(self)
        } else {
            if (!owner.compareAndSet(NO_OWNER, self)) {
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
        return if (owner.value == self) {
            reEnterCount += 1
            true
        } else if (waiters.incrementAndGet() == 1 && owner.compareAndSet(NO_OWNER, self)) {
            true
        } else {
            waiters.decrementAndGet()
            false
        }
    }


    private fun waitForUnlockAndLock(self: Long) {
        withMonitor(monitor) {
            while (!owner.compareAndSet(NO_OWNER, self)) {
                monitor.waitWithDonation(owner.value)
            }
        }
    }

    public fun unlock() {
        require (owner.value == currentThreadId)
        if (reEnterCount > 0) {
            reEnterCount -= 1
        } else {
            owner.value = NO_OWNER
            if (waiters.decrementAndGet() > 0) {
                withMonitor(monitor) {
                    // We expect the highest priority thread to be woken up, but this should work
                    // in any case.
                    monitor.nativeMutex.notify()
                }
            }
        }
    }

    private inline fun withMonitor(monitor: DonatingMonitor, block: () -> Unit) {
        monitor.nativeMutex.enter()
        return try {
            block()
        } finally {
            monitor.nativeMutex.exit()
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
            val ourQosClass = qos_class_self() as UInt
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
                memScoped {
                    val lockOwnerQosClass = alloc<UIntVar>()
                    val lockOwnerRelPrio = alloc<IntVar>()
                    pthread_get_qos_class_np(lockOwner.toCPointer(), lockOwnerQosClass.ptr, lockOwnerRelPrio.ptr)
                    if (ourQosClass > lockOwnerQosClass.value) {
                        qosOverride = pthread_override_qos_class_start_np(lockOwner.toCPointer(), qos_class_self(), 0)
                        qosOverrideQosClass = ourQosClass
                    }
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


@OptIn(ExperimentalForeignApi::class)
private class NativeMutexNode {
    var next: NativeMutexNode? = null

    private val arena: Arena = Arena()
    private val cond: pthread_cond_t = arena.alloc()
    private val mutex: pthread_mutex_t = arena.alloc()
    private val attr: pthread_mutexattr_t = arena.alloc()

    init {
        require (pthread_cond_init(cond.ptr, null) == 0)
        require(pthread_mutexattr_init(attr.ptr) == 0)
        require (pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_ERRORCHECK) == 0)
        require(pthread_mutex_init(mutex.ptr, attr.ptr) == 0)
    }

    fun enter() = require(pthread_mutex_lock(mutex.ptr) == 0)

    fun exit() = require(pthread_mutex_unlock(mutex.ptr) == 0)

    fun wait() = require(pthread_cond_wait(cond.ptr, mutex.ptr) == 0)

    fun notify() = require (pthread_cond_signal(cond.ptr) == 0)

    fun dispose() {
        pthread_cond_destroy(cond.ptr)
        pthread_mutex_destroy(mutex.ptr)
        pthread_mutexattr_destroy(attr.ptr)
        arena.clear()
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
            if (oldTop == null)
                return null
            val newHead = oldTop.next
            if (top.compareAndSet(oldTop, newHead)) {
                size.decrementAndGet()
                return oldTop
            }
        }
    }
}



