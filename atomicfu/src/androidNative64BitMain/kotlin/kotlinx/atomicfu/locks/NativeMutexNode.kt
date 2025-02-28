/*
 * Copyright 2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package kotlinx.atomicfu.locks

import kotlinx.cinterop.Arena
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual class NativeMutexNode {
    actual var next: NativeMutexNode? = null

    private val arena: Arena = Arena()
    private val cond: pthread_cond_t = arena.alloc()
    private val mutex: pthread_mutex_t = arena.alloc()
    private val attr: pthread_mutexattr_tVar = arena.alloc()

    init {
        require(pthread_cond_init(cond.ptr, null) == 0)
        require(pthread_mutexattr_init(attr.ptr) == 0)
        require(pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_ERRORCHECK.toInt()) == 0)
        require(pthread_mutex_init(mutex.ptr, attr.ptr) == 0)
    }

    actual fun lock() = require(pthread_mutex_lock(mutex.ptr) == 0)

    actual fun unlock() = require(pthread_mutex_unlock(mutex.ptr) == 0)

    actual fun wait(lockOwner: Long) = require(pthread_cond_wait(cond.ptr, mutex.ptr) == 0)

    actual fun notify() = require(pthread_cond_signal(cond.ptr) == 0)

    actual fun dispose() {
        pthread_cond_destroy(cond.ptr)
        pthread_mutex_destroy(mutex.ptr)
        pthread_mutexattr_destroy(attr.ptr)
        arena.clear()
    }
}
