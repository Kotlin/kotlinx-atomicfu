package kotlinx.atomicfu.locks

import interop.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.*

public class NativeMutexNode {
    private val mutex: CPointer<lock_support_t> = lock_support_init()!!

    internal var next: NativeMutexNode? = null

    fun lock() {
        interop.lock(mutex)
    }

    fun unlock() {
        interop.unlock(mutex)
    }
}

/**
 * This is a trivial counter-part of NativeMutexNode that does not rely on interop.def
 * The problem is, commonizer cannot commonize pthreads, thus this declaration should be duplicated
 * over multiple Native source-sets to work properly
 */
//public class NativeMutexNode {
//
//    @Volatile
//    private var isLocked = false
//    private val pMutex = nativeHeap.alloc<pthread_mutex_t>().apply { pthread_mutex_init(ptr, null) }
//    private val pCond = nativeHeap.alloc<pthread_cond_t>().apply { pthread_cond_init(ptr, null) }
//
//    internal var next: NativeMutexNode? = null
//
//    fun lock() {
//        pthread_mutex_lock(pMutex.ptr)
//        while (isLocked) { // wait till locked are available
//            pthread_cond_wait(pCond.ptr, pMutex.ptr)
//        }
//        isLocked = true
//        pthread_mutex_unlock(pMutex.ptr)
//    }
//
//    fun unlock() {
//        pthread_mutex_lock(pMutex.ptr)
//        isLocked = false
//        pthread_cond_broadcast(pCond.ptr)
//        pthread_mutex_unlock(pMutex.ptr)
//    }
//}