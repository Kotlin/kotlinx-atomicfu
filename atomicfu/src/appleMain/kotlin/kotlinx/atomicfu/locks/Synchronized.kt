package kotlinx.atomicfu.locks

import kotlin.native.internal.createCleaner
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_trylock
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t
                  
actual open class SynchronizedObject {

    private val mutex: pthread_mutex_t = nativeHeap.alloc()

    init {
        pthread_mutex_init(mutex.ptr, RECURSIVE_ATTR.ptr)
    }

    @Suppress("unused") // The returned Cleaner must be assigned to a property
    @ExperimentalStdlibApi
    private val cleaner =
        createCleaner(mutex) {
            pthread_mutex_destroy(it.ptr)
            nativeHeap.free(it)
        }

    fun lock() {
        pthread_mutex_lock(mutex.ptr)
    }

    fun unlock() {
        pthread_mutex_unlock(mutex.ptr)
    }

    fun tryLock(): Boolean = pthread_mutex_trylock(mutex.ptr) == 0

    private companion object {
        private val RECURSIVE_ATTR: pthread_mutexattr_t = nativeHeap.alloc()

        init {
            pthread_mutexattr_init(RECURSIVE_ATTR.ptr)
            pthread_mutexattr_settype(RECURSIVE_ATTR.ptr, platform.posix.PTHREAD_MUTEX_RECURSIVE)
        }
    }
}

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
