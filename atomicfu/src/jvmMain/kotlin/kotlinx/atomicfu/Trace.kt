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
    override fun format(index: Int, text: String): String =
        "$index: [${Thread.currentThread().name}] $text"
}

private class NamedTrace(
    private val trace: TraceBase, 
    private val name: String
) : TraceBase() {
    override fun append(text: String) = trace.append("$name.$text")
    override fun toString(): String = trace.toString()
}

private class TraceImpl(size: Int, val format: TraceFormat) : TraceBase() {
    init { require(size >= 1) }
    private val size = ((size shl 1) - 1).takeHighestOneBit() // next power of 2
    private val mask = this.size - 1
    private val trace = arrayOfNulls<String>(this.size)
    private val index = AtomicInteger(0)

    override fun append(text: String) {
        val i = index.getAndIncrement()
        trace[i and mask] = format.format(i, text)
    }

    override fun toString(): String = buildString {
        val start = index.get() and mask
        var i = start
        var cnt = 0
        do {
            val s = trace[i]
            if (s != null) {
                if (cnt++ > 0) append('\n')
                append(s)
            }
            i = (i + 1) and mask
        } while (i != start)
    }
}