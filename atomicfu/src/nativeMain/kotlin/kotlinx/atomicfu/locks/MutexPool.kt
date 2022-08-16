package kotlinx.atomicfu.locks

import interop.*
import kotlinx.cinterop.*
import kotlin.native.concurrent.*
import kotlin.native.internal.NativePtr

private const val INITIAL_POOL_CAPACITY = 64

internal object MutexPool {

    private val top = AtomicNativePtr(NativePtr.NULL)
    private val mutexes = nativeHeap.allocArray<mutex_node_t>(INITIAL_POOL_CAPACITY) { mutex_node_init(ptr) }

    init {
        for (i in 0 until INITIAL_POOL_CAPACITY) {
            release(interpretCPointer<mutex_node_t>(mutexes.rawValue.plus(i * sizeOf<mutex_node_t>()))!!)
        }
    }

    private fun allocMutexNode() = nativeHeap.alloc<mutex_node_t> { mutex_node_init(ptr) }.ptr

    fun allocate(): CPointer<mutex_node_t> = pop() ?: allocMutexNode()

    internal fun release(mutexNode: CPointer<mutex_node_t>) {
        while (true) {
            val oldTop = interpretCPointer<mutex_node_t>(top.value)
            mutexNode.pointed.next = oldTop
            if (top.compareAndSet(oldTop.rawValue, mutexNode.rawValue))
                return
        }
    }

    private fun pop(): CPointer<mutex_node_t>? {
        while (true) {
            val oldTop = interpretCPointer<mutex_node_t>(top.value)
            if (oldTop.rawValue === NativePtr.NULL)
                return null
            val newHead = oldTop!!.pointed.next
            if (top.compareAndSet(oldTop.rawValue, newHead.rawValue))
                return oldTop
        }
    }
}
