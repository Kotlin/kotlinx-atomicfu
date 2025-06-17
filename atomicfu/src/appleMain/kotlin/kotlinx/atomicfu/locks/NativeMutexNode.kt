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
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
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
import platform.posix.pthread_get_qos_class_np
import platform.posix.pthread_override_t
import platform.posix.pthread_override_qos_class_end_np
import platform.posix.pthread_override_qos_class_start_np
import platform.posix.qos_class_self

import platform.posix.PTHREAD_MUTEX_ERRORCHECK

@OptIn(ExperimentalForeignApi::class)
public actual class NativeMutexNode {
    internal actual var next: NativeMutexNode? = null

    private val arena: Arena = Arena()
    private val cond: pthread_cond_t = arena.alloc()
    private val mutex: pthread_mutex_t = arena.alloc()
    private val attr: pthread_mutexattr_t = arena.alloc()
    private var qosOverride: pthread_override_t? = null
    private var qosOverrideQosClass: UInt = 0U

    // Used locally as return parameters in donateQos
    private val lockOwnerQosClass = arena.alloc<UIntVar>()
    private val lockOwnerRelPrio = arena.alloc<IntVar>()

    init {
        require(pthread_cond_init(cond.ptr, null) == 0)
        require(pthread_mutexattr_init(attr.ptr) == 0)
        require(pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_ERRORCHECK) == 0)
        require(pthread_mutex_init(mutex.ptr, attr.ptr) == 0)
    }

    public actual fun lock() {
        pthread_mutex_lock(mutex.ptr)
    }

    public actual fun unlock() {
        pthread_mutex_unlock(mutex.ptr)
    }

    internal actual fun notify() {
        pthread_cond_signal(cond.ptr)
    }

    internal actual fun wait(lockOwner: Long) {
        donateQos(lockOwner)
        require(pthread_cond_wait(cond.ptr, mutex.ptr) == 0)
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
            pthread_get_qos_class_np(lockOwner.toCPointer(), lockOwnerQosClass.ptr, lockOwnerRelPrio.ptr)
            if (ourQosClass > lockOwnerQosClass.value) {
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

    internal actual fun dispose() {
        pthread_cond_destroy(cond.ptr)
        pthread_mutex_destroy(mutex.ptr)
        pthread_mutexattr_destroy(attr.ptr)
        arena.clear()
    }
}
