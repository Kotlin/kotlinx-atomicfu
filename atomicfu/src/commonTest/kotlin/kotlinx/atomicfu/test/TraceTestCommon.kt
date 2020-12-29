/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import internal_test2.Updater
import kotlinx.atomicfu.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterWithDefaultTrace {
    private val a = atomic(0)

    private val defaultTrace = Trace()
    private val a1 = atomic(5, defaultTrace)

    fun inc(): Int {
        val x = a.incrementAndGet()
        return x
    }

    fun multTen(): Boolean {
        val oldValue = a1.value
        defaultTrace { "current value = $oldValue" }
        return a1.compareAndSet(oldValue, oldValue * 10)
    }

    internal fun getA() = a.value
    internal fun getA1() = a1.value
}

class CounterWithCustomSizeTrace {
    private val t = Trace(30)
    private val a = atomic(0, t)

    fun dec(): Int {
        t { "current value = ${a.value}" }
        return a.getAndDecrement()
    }
    internal fun get() = a.value
}

class CounterWithCustomSizeAndFuncTrace {
    private val t = Trace(30, TraceFormat { id, text -> "$id: $text"})
    private val a = atomic(0)

    fun dec(): Int {
        t { "current value = ${a.value}" }
        return a.getAndDecrement()
    }
    internal fun get() = a.value
}

class CounterWithInternalTrace {
    private val u = Updater()
    private val a = atomic(0, u.internalTrace)

    fun update() {
        val oldValue = a.value
        u.internalTrace { "old value = $oldValue" }
        a.compareAndSet(oldValue, oldValue + 5)
    }
    internal fun get() = a.value
}

class InternalTraceTest {
    @Test
    fun testInternalTrace() {
        val cit = CounterWithInternalTrace()
        repeat(5) { cit.update() }
        assertEquals(cit.get(), 25)
    }
}

class MyCounter {
    @Test
    fun testEasy() {
        val c = CounterWithDefaultTrace()
        assertEquals(0, c.getA())
        c.inc()
        assertEquals(1, c.getA())
        c.multTen()
        assertEquals(c.getA1(), 50)
    }

    @Test
    fun testCustomSizeTrace() {
        val n = 1000
        val c = CounterWithCustomSizeTrace()
        repeat(n) {
            c.dec()
        }
        assertEquals(-n, c.get())
    }

    @Test
    fun testCustomSizeAndFuncTrace() {
        val n = 1000
        val c = CounterWithCustomSizeAndFuncTrace()
        repeat(n) {
            c.dec()
        }
        assertEquals(-n, c.get())
    }
}

class TraceAppendOverridesTest {
    private val aTrace = Trace(format = TraceFormat { i, text -> "[$i: $text]" })
    private val a = atomic(0, aTrace)

    private enum class Status { START, END }

    fun inc(n: Int, i: Int) {
        aTrace.append(i, n, Status.START)
        val res = a.getAndAdd(n)
        aTrace.append(i, n, res, Status.END)
    }

    @Test
    fun testTraceAppendOverrides() {
        val n = 1000
        repeat(n) {
            inc(1, it)
        }
        assertEquals(n, a.value)
    }
}