/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("RedundantVisibilityModifier")

package kotlinx.atomicfu

import java.util.*
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

private const val PAUSE_EVERY_N_STEPS = 1000
private const val STALL_LIMIT_MS = 15_000L // 15s
private const val SHUTDOWN_CHECK_MS = 10L // 10ms

private const val STATUS_DONE = Int.MAX_VALUE

private const val MAX_PARK_NANOS = 1_000_000L // part for at most 1ms just in case of loosing unpark signal

/**
 * Environment for performing lock-freedom tests for lock-free data structures
 * that are written with [atomic] variables.
 */
public open class LockFreedomTestEnvironment(
    private val name: String,
    private val allowSuspendedThreads: Int = 0
) {
    private val interceptor = Interceptor()
    private val threads = mutableListOf<TestThread>()
    private val performedOps = LongAdder()
    private val uncaughtException = AtomicReference<Throwable?>()
    private var started = false
    private var performedResumes = 0

    @Volatile
    private var completed = false
    private val onCompletion = mutableListOf<() -> Unit>()

    private val ueh = Thread.UncaughtExceptionHandler { t, e ->
        synchronized(System.out) {
            println("Uncaught exception in thread $t")
            e.printStackTrace(System.out)
            uncaughtException.compareAndSet(null, e)
        }
    }

    // status < 0             - inv paused thread id
    // status >= 0            - no. of performed resumes so far (==last epoch)
    // status == STATUS_DONE - done working
    private val status = AtomicInteger()
    private val globalPauseProgress = AtomicInteger()
    private val suspendedThreads = ArrayList<TestThread>()

    @Volatile
    private var isActive = true

    // ---------- API ----------

    /**
     * Starts lock-freedom test for a given duration in seconds,
     * invoking [progress] every second (it will be invoked `seconds + 1` times).
     */
    public fun performTest(seconds: Int, progress: () -> Unit = {}) {
        check(isActive) { "Can perform test at most once on this instance" }
        println("=== $name")
        val minThreads = 2 + allowSuspendedThreads
        check(threads.size >= minThreads) { "Must define at least $minThreads test threads" }
        lockAndSetInterceptor(interceptor)
        started = true
        var nextTime = System.currentTimeMillis()
        threads.forEach { thread ->
            thread.setUncaughtExceptionHandler(ueh)
            thread.lastOpTime = nextTime
            thread.start()
        }
        try {
            var second = 0
            while (uncaughtException.get() == null) {
                waitUntil(nextTime)
                println("--- $second: Performed ${performedOps.sum()} operations${resumeStr()}")
                progress()
                checkStalled()
                if (++second > seconds) break
                nextTime += 1000L
            }
        } finally {
            complete()
        }
        println("------ Done with ${performedOps.sum()} operations${resumeStr()}")
        progress()
    }

    private fun complete() {
        val activeNonPausedThreads: MutableMap<TestThread, Array<StackTraceElement>> = mutableMapOf()
        val shutdownDeadline = System.currentTimeMillis() + STALL_LIMIT_MS
        try {
            completed = true
            // perform custom completion blocks. For testing of things like channels, these custom completion
            // blocks close all the channels, so that all suspended coroutines shall get resumed.
            onCompletion.forEach { it() }
            // signal shutdown to all threads (non-paused threads will terminate)
            isActive = false
            // wait for threads to terminate
            while (System.currentTimeMillis() < shutdownDeadline) {
                // Check all threads while shutting down:
                // All terminated threads are considered to make progress for the purpose of resuming stalled ones
                activeNonPausedThreads.clear()
                for (t in threads) {
                    when {
                        !t.isAlive -> t.makeProgress(getPausedEpoch()) // not alive - makes progress
                        t.index.inv() == status.get() -> {} // active, paused -- skip
                        else -> {
                            val stackTrace = t.stackTrace
                            if (t.isAlive) activeNonPausedThreads[t] = stackTrace
                        }
                    }
                }
                if (activeNonPausedThreads.isEmpty()) break
                checkStalled()
                Thread.sleep(SHUTDOWN_CHECK_MS)
            }
            activeNonPausedThreads.forEach { (t, stackTrack) ->
                println("=== $t had failed to shutdown in time")
                stackTrack.forEach { println("\tat $it") }
            }
        } finally {
            shutdown(shutdownDeadline)
        }
        // if no other exception was throws & we had threads that did not shut down -- still fails
        if (activeNonPausedThreads.isNotEmpty()) error("Some threads had failed to shutdown in time")
    }

    private fun shutdown(shutdownDeadline: Long) {
        // forcefully unpause paused threads to shut them down (if any left)
        val curStatus = status.getAndSet(STATUS_DONE)
        if (curStatus < 0) LockSupport.unpark(threads[curStatus.inv()])
        threads.forEach {
            val remaining = shutdownDeadline - System.currentTimeMillis()
            if (remaining > 0) it.join(remaining)
        }
        // abort waiting threads (if still any left)
        threads.forEach { it.abortWait() }
        // cleanup & be done
        unlockAndResetInterceptor(interceptor)
        uncaughtException.get()?.let { throw it }
        threads.find { it.isAlive }?.let { dumpThreadsError("A thread is still alive: $it")}
    }

    private fun checkStalled() {
        val stallLimit = System.currentTimeMillis() - STALL_LIMIT_MS
        val stalled = threads.filter { it.lastOpTime < stallLimit }
        if (stalled.isNotEmpty()) dumpThreadsError("Progress stalled in threads ${stalled.map { it.name }}")
    }

    private fun resumeStr(): String {
        val resumes = performedResumes
        return if (resumes == 0) "" else " (pause/resumes $resumes)"
    }

    private fun waitUntil(nextTime: Long) {
        while (true) {
            val curTime = System.currentTimeMillis()
            if (curTime >= nextTime) break
            Thread.sleep(nextTime - curTime)
        }
    }

    private fun dumpThreadsError(message: String) : Nothing {
        val traces = threads.associate { it to it.stackTrace }
        println("!!! $message")
        println("=== Dumping live thread stack traces")
        for ((thread, trace) in traces) {
            if (trace.isEmpty()) continue
            println("Thread \"${thread.name}\" ${thread.state}")
            for (t in trace) println("\tat ${t.className}.${t.methodName}(${t.fileName}:${t.lineNumber})")
            println()
        }
        println("===")
        error(message)
    }

    /**
     * Returns true when test was completed.
     * Sets to true before calling [onCompletion] blocks.
     */
    public val isCompleted: Boolean get() = completed

    /**
     * Performs a given block of code on test's completion
     */
    public fun onCompletion(block: () -> Unit) {
        onCompletion += block
    }

    /**
     * Creates a new test thread in this environment that is executes a given lock-free [operation]
     * in a loop while this environment [isActive].
     */
    public fun testThread(name: String? = null, operation: suspend TestThread.() -> Unit): TestThread =
        TestThread(name, operation)

    /**
     * Test thread.
     */
    @Suppress("LeakingThis")
    public inner class TestThread internal constructor(
        name: String?,
        private val operation: suspend TestThread.() -> Unit
    ) : Thread(composeThreadName(name)) {
        internal val index: Int

        internal @Volatile var lastOpTime = 0L
        internal @Volatile var pausedEpoch = -1

        private val random = Random()

        // thread-local stuff
        private var operationEpoch = -1
        private var progressEpoch = -1
        private var sink = 0

        init {
            check(!started)
            index = threads.size
            threads += this
        }

        public override fun run() {
            while (isActive) {
                callOperation()
            }
        }

        /**
         * Use it to insert an arbitrary intermission between lock-free operations.
         */
        public inline fun <T> intermission(block: () -> T): T {
            afterLockFreeOperation()
            return try { block() }
                finally { beforeLockFreeOperation() }
        }

        @PublishedApi
        internal fun beforeLockFreeOperation() {
            operationEpoch = getPausedEpoch()
        }

        @PublishedApi
        internal fun afterLockFreeOperation() {
            makeProgress(operationEpoch)
            lastOpTime = System.currentTimeMillis()
            performedOps.add(1)
        }

        internal fun makeProgress(epoch: Int) {
            if (epoch <= progressEpoch) return
            progressEpoch = epoch
            val total = globalPauseProgress.incrementAndGet()
            if (total >= threads.size - 1) {
                check(total == threads.size - 1)
                check(globalPauseProgress.compareAndSet(threads.size - 1, 0))
                resumeImpl()
            }
        }

        /**
         * Inserts random spin wait between multiple lock-free operations in [operation].
         */
        public fun randomSpinWaitIntermission() {
            intermission {
                if (random.nextInt(100) < 95) return // be quick, no wait 95% of time
                do {
                    val x = random.nextInt(100)
                    repeat(x) { sink += it }
                } while (x >= 90)
            }
        }

        internal fun stepImpl() {
            if (random.nextInt(PAUSE_EVERY_N_STEPS) == 0) pauseImpl()
        }

        internal fun pauseImpl() {
            while (true) {
                val curStatus = status.get()
                if (curStatus < 0 || curStatus == STATUS_DONE) return // some other thread paused or done
                pausedEpoch = curStatus + 1
                val newStatus = index.inv()
                if (status.compareAndSet(curStatus, newStatus)) {
                    while (status.get() == newStatus) LockSupport.parkNanos(MAX_PARK_NANOS) // wait
                    return
                }
            }
        }

        // ----- Lightweight support for suspending operations -----

        private fun callOperation() {
            beforeLockFreeOperation()
            beginRunningOperation()
            val result = operation.startCoroutineUninterceptedOrReturn(this, completion)
            when {
                result === Unit -> afterLockFreeOperation() // operation completed w/o suspension -- done
                result === COROUTINE_SUSPENDED -> waitUntilCompletion() // operation had suspended
                else -> error("Unexpected result of operation: $result")
            }
            try {
                doneRunningOperation()
            } catch(e: IllegalStateException) {
                throw IllegalStateException("${e.message}; original start result=$result", e)
            }
        }

        private var runningOperation = false
        private var result: Result<Any?>? = null
        private var continuation: Continuation<Any?>? = null

        private fun waitUntilCompletion() {
            try {
                while (true) {
                    afterLockFreeOperation()
                    val result: Result<Any?> = waitForResult()
                    val continuation = takeContinuation()
                    if (continuation == null) { // done
                        check(result.getOrThrow() === Unit)
                        return
                    }
                    removeSuspended(this)
                    beforeLockFreeOperation()
                    continuation.resumeWith(result)
                }
            } finally {
                removeSuspended(this)
            }
        }

        private fun beginRunningOperation() {
            runningOperation = true
            result = null
            continuation = null
        }

        @Synchronized
        private fun doneRunningOperation() {
            check(runningOperation) { "Should be running operation" }
            check(result == null && continuation == null) {
                "Callback invoked with result=$result, continuation=$continuation"
            }
            runningOperation = false
        }

        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        @Synchronized
        private fun resumeWith(result: Result<Any?>, continuation: Continuation<Any?>?) {
            check(runningOperation) { "Should be running operation" }
            check(this.result == null && this.continuation == null) {
                "Resumed again with result=$result, continuation=$continuation, when this: result=${this.result}, continuation=${this.continuation}"
            }
            this.result = result
            this.continuation = continuation
            (this as Object).notifyAll()
        }

        @Suppress("RESULT_CLASS_IN_RETURN_TYPE", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        @Synchronized
        private fun waitForResult(): Result<Any?> {
            while (true) {
                val result = this.result
                if (result != null) return result
                val index = addSuspended(this)
                if (index < allowSuspendedThreads) {
                    // This suspension was permitted, so assume progress is happening while it is suspended
                    makeProgress(getPausedEpoch())
                }
                (this as Object).wait(10) // at most 10 ms
            }
        }

        @Synchronized
        private fun takeContinuation(): Continuation<Any?>? =
            continuation.also {
                this.result = null
                this.continuation = null
            }

        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        @Synchronized
        fun abortWait() {
            this.result = Result.failure(IllegalStateException("Aborted at the end of test"))
            (this as Object).notifyAll()
        }

        private val interceptor: CoroutineContext = object : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
            override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
                Continuation<T>(this) {
                    @Suppress("UNCHECKED_CAST")
                    resumeWith(it, continuation as Continuation<Any?>)
                }
        }

        private val completion = Continuation<Unit>(interceptor) {
            resumeWith(it, null)
        }
    }

    // ---------- Implementation ----------

    @Synchronized
    private fun addSuspended(thread: TestThread): Int {
        val index = suspendedThreads.indexOf(thread)
        if (index >= 0) return index
        suspendedThreads.add(thread)
        return suspendedThreads.size - 1
    }

    @Synchronized
    private fun removeSuspended(thread: TestThread) {
        suspendedThreads.remove(thread)
    }

    private fun getPausedEpoch(): Int {
        while (true) {
            val curStatus = status.get()
            if (curStatus >= 0) return -1 // not paused
            val thread = threads[curStatus.inv()]
            val pausedEpoch = thread.pausedEpoch
            if (curStatus == status.get()) return pausedEpoch
        }
    }

    internal fun step() {
        val thread = Thread.currentThread() as? TestThread ?: return
        thread.stepImpl()
    }

    private fun resumeImpl() {
        while (true) {
            val curStatus = status.get()
            if (curStatus == STATUS_DONE) return // done
            check(curStatus < 0)
            val thread = threads[curStatus.inv()]
            performedResumes = thread.pausedEpoch
            if (status.compareAndSet(curStatus, thread.pausedEpoch)) {
                LockSupport.unpark(thread)
                return
            }
        }
    }

    private fun composeThreadName(threadName: String?): String {
        if (threadName != null) return "$name-$threadName"
        return name + "-${threads.size + 1}"
    }

    private inner class Interceptor : AtomicOperationInterceptor() {
        override fun <T> beforeUpdate(ref: AtomicRef<T>) = step()
        override fun beforeUpdate(ref: AtomicInt) = step()
        override fun beforeUpdate(ref: AtomicLong) = step()
        override fun <T> afterSet(ref: AtomicRef<T>, newValue: T) = step()
        override fun afterSet(ref: AtomicInt, newValue: Int) = step()
        override fun afterSet(ref: AtomicLong, newValue: Long) = step()
        override fun <T> afterRMW(ref: AtomicRef<T>, oldValue: T, newValue: T) = step()
        override fun afterRMW(ref: AtomicInt, oldValue: Int, newValue: Int) = step()
        override fun afterRMW(ref: AtomicLong, oldValue: Long, newValue: Long) = step()
        override fun toString(): String = "LockFreedomTestEnvironment($name)"
    }
}

/**
 * Manual pause for on-going lock-free operation in a specified piece of code.
 * Use it for targeted debugging of specific places in code. It does nothing
 * when invoked outside of test thread.
 *
 * **Don't use it in production code.**
 */
public fun pauseLockFreeOp() {
    val thread = Thread.currentThread() as? LockFreedomTestEnvironment.TestThread ?: return
    thread.pauseImpl()
}