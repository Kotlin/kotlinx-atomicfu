package kotlinx.atomicfu.locks

import kotlinx.cinterop.*

public class NativeMutexNode {
    private val mutex: interop.mutex_node_t = nativeHeap.alloc<interop.mutex_node_t>().also {
        interop.mutex_node_init(it.ptr)
    }

    internal var next: NativeMutexNode? = null

    fun lock() {
        interop.lock(mutex.mutex)
    }

    fun unlock() {
        interop.unlock(mutex.mutex)
    }
}

