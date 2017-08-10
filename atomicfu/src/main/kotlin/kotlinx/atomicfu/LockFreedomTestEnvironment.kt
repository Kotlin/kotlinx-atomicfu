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

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.LockSupport

private const val PAUSE_EVERY_N_STEPS = 1000
private const val STALL_LIMIT_MS = 2000L // 2 sec

/**
 * Environment for performing lock-freedom tests for lock-free data structures
 * that are written with [atomic] variables.
 */
public open class LockFreedomTestEnvironment(
    private val name: String
) {
    private val interceptor = Interceptor()
    private val threads = mutableListOf<TestThread>()
    private var started = false
    private val performedOps = LongAdder()
    private val uncaughtException = AtomicReference<Throwable?>()

    private val ueh = Thread.UncaughtExceptionHandler { t, e ->
        synchronized(System.out) {
            println("Uncaught exception in thread $t")
            e.printStackTrace(System.out)
            uncaughtException.compareAndSet(null, e)
        }
    }

    @Volatile private var isActive = false
    @Volatile private var performedResumes = 0
    private val pausedThread = AtomicReference<TestThread?>()
    private val globalPauseProgress = AtomicInteger()

    // ---------- API ----------

    /**
     * Starts lock-freedom test for a given duration in seconds,
     * invoking [progress] every second (it will be invoked `seconds + 1` times).
     */
    public fun performTest(seconds: Int, progress: () -> Unit = {}) {
        println("=== $name")
        check(threads.size >= 2) { "Must define at least two test threads" }
        lockAndSetInterceptor(interceptor)
        started = true
        isActive = true
        var nextTime = System.currentTimeMillis()
        threads.forEach { thread ->
            thread.setUncaughtExceptionHandler(ueh)
            thread.lastOpTime = nextTime
            thread.start()
        }
        var second = 0
        while (true) {
            waitUntil(nextTime)
            val ops = performedOps.sum()
            val resumes = performedResumes
            val resumesStr = if (resumes == 0) "" else " (pause/resumes $resumes)"
            println("--- $second: Performed $ops operations$resumesStr")
            progress()
            val stallLimit = System.currentTimeMillis() - STALL_LIMIT_MS
            val stalled = threads.filter { it.lastOpTime < stallLimit }
            if (stalled.isNotEmpty()) dumpThreadsError("Progress stalled in threads ${stalled.map { it.name }}")
            if (++second > seconds) break
            nextTime += 1000L
        }
        isActive = false
        pausedThread.get()?.let {
            pausedThread.set(null)
            LockSupport.unpark(it)
        }
        threads.forEach { it.join(1000L) }
        unlockAndResetInterceptor(interceptor)
        uncaughtException.get()?.let { throw it }
        threads.find { it.isAlive }?.let { dumpThreadsError("A thread is still alive: $it")}
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
    public inline fun testThread(name: String? = null, crossinline operation: () -> Unit) =
        object : TestThread(name) {
            override fun operation() = operation()
        }

    /**
     * Test thread.
     */
    @Suppress("LeakingThis")
    public abstract inner class TestThread(name: String?) : Thread(composeThreadName(name)) {
        @Volatile var lastOpTime = 0L
        @Volatile var pausedEpoch = -1
        var progressEpoch = -1 // thread-local

        init {
            check(!started)
            threads += this
        }

        public final override fun run() {
            while (isActive) {
                ensureLockFree {
                    operation()
                }
                lastOpTime = System.currentTimeMillis()
                performedOps.add(1)
            }
        }

        public abstract fun operation()
    }

    // ---------- Implementation ----------

    /**
     * Wrapper around lock-free operations.
     */
    private inline fun <T> ensureLockFree(operation: () -> T): T {
        val epoch = getPausedEpoch()
        return try {
            operation()
        } finally {
            if (epoch >= 0) onProgressDuringPause(epoch)
        }
    }

    private fun getPausedEpoch(): Int {
        while (true) {
            val thread = pausedThread.get() ?: return -1
            val epoch = thread.pausedEpoch
            if (thread == pausedThread.get()) return epoch
        }
    }

    internal fun step() {
        if (ThreadLocalRandom.current().nextInt(PAUSE_EVERY_N_STEPS) == 0) pause()
    }

    private fun pause() {
        if (pausedThread.get() != null) return
        val self = Thread.currentThread() as TestThread
        self.pausedEpoch = performedResumes
        if (!pausedThread.compareAndSet(null, self)) return
        while (pausedThread.get() == self) {
            LockSupport.park()
        }
    }

    private fun onProgressDuringPause(epoch: Int) {
        val self = Thread.currentThread() as TestThread
        if (epoch <= self.progressEpoch) return
        self.progressEpoch = epoch
        val total = globalPauseProgress.incrementAndGet()
        if (total >= threads.size - 1) {
            check(total == threads.size - 1)
            resume()
        }
    }

    private fun resume() {
        val thread = pausedThread.get()
        check(globalPauseProgress.compareAndSet(threads.size - 1, 0))
        check(thread != null)
        performedResumes++
        check(pausedThread.compareAndSet(thread, null))
        LockSupport.unpark(thread)
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