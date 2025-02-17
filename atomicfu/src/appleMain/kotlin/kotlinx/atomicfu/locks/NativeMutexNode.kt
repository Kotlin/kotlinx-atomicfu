/*
 * Copyright 2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package kotlinx.atomicfu.locks

import kotlinx.cinterop.Arena
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import platform.posix.pthread_cond_destroy
import platform.posix.pthread_cond_init
import platform.posix.pthread_cond_signal
import platform.posix.pthread_cond_t
import platform.posix.pthread_cond_wait
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t

import platform.posix.PTHREAD_MUTEX_ERRORCHECK

@OptIn(ExperimentalForeignApi::class)
internal class NativeMutexNode {
    var next: NativeMutexNode? = null

    private val arena: Arena = Arena()
    private val cond: pthread_cond_t = arena.alloc()
    private val mutex: pthread_mutex_t = arena.alloc()
    private val attr: pthread_mutexattr_t = arena.alloc()

    // Used locally as return parameters in donateQos
    val lockOwnerQosClass = arena.alloc<UIntVar>()
    val lockOwnerRelPrio = arena.alloc<IntVar>()

    init {
        require(pthread_cond_init(cond.ptr, null) == 0)
        require(pthread_mutexattr_init(attr.ptr) == 0)
        require(pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_ERRORCHECK) == 0)
        require(pthread_mutex_init(mutex.ptr, attr.ptr) == 0)
    }

    fun lock() = require(pthread_mutex_lock(mutex.ptr) == 0)

    fun unlock() = require(pthread_mutex_unlock(mutex.ptr) == 0)

    fun wait() = require(pthread_cond_wait(cond.ptr, mutex.ptr) == 0)

    fun notify() = require(pthread_cond_signal(cond.ptr) == 0)

    fun dispose() {
        pthread_cond_destroy(cond.ptr)
        pthread_mutex_destroy(mutex.ptr)
        pthread_mutexattr_destroy(attr.ptr)
        arena.clear()
    }
}
