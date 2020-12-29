/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

class TraceToStringTest {
    private val aTrace = Trace(format = TraceFormat { i, text -> "[$i: $text]" })
    private val a = atomic(0, aTrace)

    private val shortTrace = Trace(4)
    private val s = atomic(0, shortTrace.named("s"))

    @Test
    fun testTraceFormat() {
        repeat(3) { i ->
            aTrace { "Iteration $i started" }
            a.lazySet(i)
            aTrace { "Iteration $i ended" }
        }
        val expected = buildString {
            repeat(3) { i ->
                if (i > 0) append('\n')
                append("""
                    [${i * 3 + 0}: Iteration $i started]
                    [${i * 3 + 1}: lazySet($i)]
                    [${i * 3 + 2}: Iteration $i ended]
                    """.trimIndent()
                )
            }
        }
        assertEquals(expected, a.trace.toString())
    }

    @Test
    fun testTraceSequence() {
        s.value = 5
        s.compareAndSet(5, -2)
        assertEquals("""
            0: s.set(5)
            1: s.CAS(5, -2)
            """.trimIndent(), s.trace.toString()
        )
        s.lazySet(3)
        s.getAndIncrement()
        s.getAndAdd(7)
        assertEquals("""
            1: s.CAS(5, -2)
            2: s.lazySet(3)
            3: s.getAndInc():3
            4: s.getAndAdd(7):4
            """.trimIndent(), s.trace.toString()
        )
        s.getAndAdd(8)
        s.getAndAdd(9)
        assertEquals("""
            3: s.getAndInc():3
            4: s.getAndAdd(7):4
            5: s.getAndAdd(8):11
            6: s.getAndAdd(9):19
            """.trimIndent(), s.trace.toString()
        )
        s.lazySet(3)
        assertEquals("""
            4: s.getAndAdd(7):4
            5: s.getAndAdd(8):11
            6: s.getAndAdd(9):19
            7: s.lazySet(3)
            """.trimIndent(), s.trace.toString()
        )
        s.getAndIncrement()
        s.getAndAdd(7)
        assertEquals("""
            6: s.getAndAdd(9):19
            7: s.lazySet(3)
            8: s.getAndInc():3
            9: s.getAndAdd(7):4
            """.trimIndent(), s.trace.toString()
        )
    }

    private enum class Status { START, END }

    @Test
    fun testMultipleAppend() {
        val i = 7
        aTrace.append(i, Status.START)
        a.lazySet(i)
        aTrace.append(i, Status.END)
        assertEquals("""
            [0: $i]
            [1: START]
            [2: lazySet($i)]
            [3: $i]
            [4: END]
            """.trimIndent(), aTrace.toString()
        )
    }
}