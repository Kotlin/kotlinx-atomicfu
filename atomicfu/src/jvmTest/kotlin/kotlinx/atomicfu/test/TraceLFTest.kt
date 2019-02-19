/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.Test

class Counter {
    private val t = Trace(64, TraceFormat { index, text ->
        "$index: [${Thread.currentThread().name}] $text"
    })
    private val a = atomic(0, t)

    fun inc(): Int {
        t { "inc() invoked" }
        val x = a.incrementAndGet()
        t { "inc() = $x" }
        return x
    }

    internal fun get() = a.value
}

class CounterDefaultAtomic {
    private val a = atomic(0)
    private val trace = Trace(64)

    fun inc(): Int {
        trace { "inc() invoked" }
        val x = a.incrementAndGet()
        trace { "inc() = $x" }
        return x
    }

    internal fun get() = a.value
}

class CounterLFTest : LockFreedomTestEnvironment("CounterLFTest") {
    private val c = Counter()
    private val c1 = CounterDefaultAtomic()

    @Test
    fun testCounterDefault() {
        repeat(10) { id ->
            testThread ("Inc-$id")  {
                c1.inc()
            }
        }
        repeat(2) { id ->
            testThread("Get-$id") {
                c1.get()
            }
        }
        performTest(10)
        println(c1.get())
    }

    @Test
    fun testLockFreedom() {
        repeat(10) { id ->
            testThread("Inc-$id") {
                c.inc()
            }
        }
        repeat(2) { id ->
            testThread("Get-$id") {
                c.get()
            }
        }
        performTest(10)
        println(c.get())
    }
}

