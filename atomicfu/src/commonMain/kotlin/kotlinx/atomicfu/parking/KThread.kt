package kotlinx.atomicfu.parking

/**
 * Holds reference to the current thread, allows for unparking it by calling [Parker.unpark].
 * Reference can be obtained by calling [currentThread].
 */
expect class KThread private constructor() {
    companion object {
        fun currentThread(): KThread
    }
}

/**
 * Parking API, uses posix on native and LockSupport on JVM.
 */
expect class Parker private constructor() {
    companion object {
        /**
         * Parks the current thread, until an [unpark] call is made.
         * Does not park the current thread if [unpark] was called before [park].
         */
        fun park(): Unit

        /**
         * Parks if [unpark] was not called before, and wakes when either an [unpark] call is made 
         * or [nanos] nanoseconds have passed.
         */
        fun parkNanos(nanos: Long): Unit

        /**
         * Unparks thread [kThread] when parked, otherwise prevents the next [park] call from parking the thread.
         * Does nothing when the thread is already pre-unparked.
         */
        fun unpark(kThread: KThread): Unit
    }
}

internal expect fun currentThreadId(): Long 

