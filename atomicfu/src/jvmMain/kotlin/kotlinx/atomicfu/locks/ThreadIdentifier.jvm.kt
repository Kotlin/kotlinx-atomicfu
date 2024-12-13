package kotlinx.atomicfu.locks

actual fun currentThreadId(): Long = Thread.currentThread().id
