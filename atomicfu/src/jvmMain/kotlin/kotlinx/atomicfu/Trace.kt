/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package kotlinx.atomicfu

import java.util.concurrent.atomic.*
import kotlin.internal.*

@Suppress("FunctionName")
@InlineOnly
public actual fun Trace(size: Int, format: TraceFormat): TraceBase =
    TraceImpl(size, format)

public actual fun TraceBase.named(name: String): TraceBase =
    if (this === TraceBase.None) this else NamedTrace(this, name)

private fun getSystemProperty(key: String): String? =
    try { System.getProperty(key) } catch (e: SecurityException) { null }

public actual val traceFormatDefault: TraceFormat =
    if (getSystemProperty("kotlinx.atomicfu.trace.thread") != null) TraceFormatThread() else TraceFormat()

private class TraceFormatThread : TraceFormat() {
    override fun format(index: Int, event: Any): String =
        "$index: [${Thread.currentThread().name}] $event"
}

private class NamedTrace(
    private val trace: TraceBase, 
    private val name: String
) : TraceBase() {
    override fun append(event: Any) = trace.append("$name.$event")

    override fun append(event1: Any, event2: Any) = trace.append("$name.$event1", "$name.$event2")

    override fun append(event1: Any, event2: Any, event3: Any) =
            trace.append("$name.$event1", "$name.$event2", "$name.$event3")

    override fun append(event1: Any, event2: Any, event3: Any, event4: Any) =
            trace.append("$name.$event1", "$name.$event2", "$name.$event3", "$name.$event4")

    override fun toString(): String = trace.toString()
}

private class TraceImpl(size: Int, private val format: TraceFormat) : TraceBase() {
    init { require(size >= 1) }
    private val size = ((size shl 1) - 1).takeHighestOneBit() // next power of 2
    private val mask = this.size - 1
    private val trace = arrayOfNulls<Any>(this.size)
    private val index = AtomicInteger(0)

    override fun append(event: Any) {
        val i = index.getAndIncrement()
        trace[i and mask] = event
    }

    override fun append(event1: Any, event2: Any) {
        val i = index.getAndAdd(2)
        trace[i and mask] = event1
        trace[(i + 1) and mask] = event2
    }

    override fun append(event1: Any, event2: Any, event3: Any) {
        val i = index.getAndAdd(3)
        trace[i and mask] = event1
        trace[(i + 1) and mask] = event2
        trace[(i + 2) and mask] = event3
    }

    override fun append(event1: Any, event2: Any, event3: Any, event4: Any) {
        val i = index.getAndAdd(4)
        trace[i and mask] = event1
        trace[(i + 1) and mask] = event2
        trace[(i + 2) and mask] = event3
        trace[(i + 3) and mask] = event4
    }

    override fun toString(): String = buildString {
        val index = index.get()
        val start = index and mask
        var i = if (index > size) index - size else 0
        var pos = start
        var cnt = 0
        do {
            val s = trace[pos]
            if (s != null) {
                if (cnt++ > 0) append('\n')
                append(format.format(i, s))
                i++
            }
            pos = (pos + 1) and mask
        } while (pos != start)
    }
}