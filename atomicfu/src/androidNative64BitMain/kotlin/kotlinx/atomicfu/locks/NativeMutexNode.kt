package kotlinx.atomicfu.locks

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.Volatile

public actual class NativeMutexNode {

    @Volatile
    private var isLocked = false
    private val pMutex = nativeHeap.alloc<pthread_mutex_t>().apply { pthread_mutex_init(ptr, null) }
    private val pCond = nativeHeap.alloc<pthread_cond_t>().apply { pthread_cond_init(ptr, null) }

    internal actual var next: NativeMutexNode? = null

    actual fun lock() {
        pthread_mutex_lock(pMutex.ptr)
        while (isLocked) { // wait till locked are available
            pthread_cond_wait(pCond.ptr, pMutex.ptr)
        }
        isLocked = true
        pthread_mutex_unlock(pMutex.ptr)
    }

   actual fun unlock() {
        pthread_mutex_lock(pMutex.ptr)
        isLocked = false
        pthread_cond_broadcast(pCond.ptr)
        pthread_mutex_unlock(pMutex.ptr)
    }
}