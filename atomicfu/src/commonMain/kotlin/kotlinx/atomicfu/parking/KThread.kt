package kotlinx.atomicfu.parking

expect class KThread private constructor() {
    companion object {
        fun currentThread(): KThread
    }
}

expect class Parker private constructor() {
    companion object {
        fun park()
        fun parkNanos(nanos: Long)
        fun unpark(kThread: KThread)
    }
}

internal expect fun currentThreadId(): Long 

