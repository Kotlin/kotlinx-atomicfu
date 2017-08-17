/*
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("RedundantVisibilityModifier")

package kotlinx.atomicfu

import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.LockSupport

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
    private val name: String
) {
    private val interceptor = Interceptor()
    private val threads = mutableListOf<TestThread>()
    private val performedOps = LongAdder()
    private val uncaughtException = AtomicReference<Throwable?>()
    private var started = false
    private var performedResumes = 0

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
        check(threads.size >= 2) { "Must define at least two test threads" }
        lockAndSetInterceptor(interceptor)
        started = true
        var nextTime = System.currentTimeMillis()
        threads.forEach { thread ->
            thread.setUncaughtExceptionHandler(ueh)
            thread.lastOpTime = nextTime
            thread.start()
        }
        var second = 0
        while (true) {
            waitUntil(nextTime)
            println("--- $second: Performed ${performedOps.sum()} operations${resumeStr()}")
            progress()
            checkStalled()
            if (++second > seconds) break
            nextTime += 1000L
        }
        // shutdown all non-paused threads first
        val shutdownDeadline = System.currentTimeMillis() + STALL_LIMIT_MS
        isActive = false
        while (System.currentTimeMillis() < shutdownDeadline) {
            if (!hasActiveNonPausedThread()) break
            checkStalled()
            Thread.sleep(SHUTDOWN_CHECK_MS)
        }
        // shutdown paused threads (if any)
        val curStatus = status.getAndSet(STATUS_DONE)
        if (curStatus < 0) LockSupport.unpark(threads[curStatus.inv()])
        threads.forEach {
            val remaining = shutdownDeadline - System.currentTimeMillis()
            if (remaining > 0) it.join(remaining)
        }
        // cleanup & be done
        unlockAndResetInterceptor(interceptor)
        uncaughtException.get()?.let { throw it }
        threads.find { it.isAlive }?.let { dumpThreadsError("A thread is still alive: $it")}
        println("------ Done with ${performedOps.sum()} operations${resumeStr()}")
        progress()
    }

    private fun checkStalled() {
        val stallLimit = System.currentTimeMillis() - STALL_LIMIT_MS
        val stalled = threads.filter { it.lastOpTime < stallLimit }
        if (stalled.isNotEmpty()) dumpThreadsError("Progress stalled in threads ${stalled.map { it.name }}")
    }

    private fun hasActiveNonPausedThread(): Boolean =
        threads.any { it.isAlive && it.index.inv() != status.get() }

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
     * Creates a new test thread in this environment that is executes a given lock-free [operation]
     * in a loop while this environment [isActive].
     */
    public inline fun testThread(name: String? = null, crossinline operation: TestThread.() -> Unit) =
        object : TestThread(name) {
            override fun operation() {
                operation.invoke(this)
            }
        }

    /**
     * Test thread.
     */
    @Suppress("LeakingThis")
    public abstract inner class TestThread(name: String?) : Thread(composeThreadName(name)) {
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

        public final override fun run() {
            while (isActive) {
                ensureLockFree {
                    operation()
                }
            }
        }

        public abstract fun operation()

        /**
         * Use it to insert an arbitrary intermission between lock-free operations.
         */
        public inline fun <T> intermission(block: () -> T): T {
            afterLockFreeOperation()
            return try { block() }
                finally { beforeLockFreeOperation() }
        }

        /**
         * Wrapper around lock-free operations.
         */
        private inline fun <T> ensureLockFree(operation: () -> T): T {
            beforeLockFreeOperation()
            return try { operation() }
                finally { afterLockFreeOperation() }
        }

        @PublishedApi
        internal fun beforeLockFreeOperation() {
            operationEpoch = getPausedEpoch()
        }

        @PublishedApi
        internal fun afterLockFreeOperation() {
            if (operationEpoch > progressEpoch) {
                progressEpoch = operationEpoch
                val total = globalPauseProgress.incrementAndGet()
                if (total >= threads.size - 1) {
                    check(total == threads.size - 1)
                    check(globalPauseProgress.compareAndSet(threads.size - 1, 0))
                    resumeImpl()
                }
            }
            lastOpTime = System.currentTimeMillis()
            performedOps.add(1)
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
    }

    // ---------- Implementation ----------

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