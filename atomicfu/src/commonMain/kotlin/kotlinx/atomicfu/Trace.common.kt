/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package kotlinx.atomicfu

import kotlin.js.JsName
import kotlin.internal.InlineOnly

/**
 * Creates `Trace` object for tracing atomic operations.
 *
 * To use a trace create a separate field for `Trace`:
 *
 * ```
 * val trace = Trace(size)
 * ```
 *
 * Using it to add trace messages:
 *
 * ```
 * trace { "Doing something" }
 * ```
 * or you can do multi-append in a garbage-free manner
 * ```
 * // Before queue.send(element) invocation
 * trace.append("Adding element to the queue", element, Thread.currentThread())
 * ```
 *
 * Pass it to `atomic` constructor to automatically trace all modifications of the corresponding field:
 *
 * ```
 * val state = atomic(initialValue, trace)
 * ```
 * An optional [named][TraceBase.named] call can be used to name all the messages related to this specific instance:
 *
 * ```
 * val state = atomic(initialValue, trace.named("state"))
 * ```
 *
 * An optional [format] parameter can be specified to add context-specific information to each trace.
 * The default format is [traceFormatDefault].
 */
@Suppress("FunctionName")
public expect fun Trace(size: Int = 32, format: TraceFormat = traceFormatDefault): TraceBase

/**
 * Adds a name to the trace. For example:
 * 
 * ```
 * val state = atomic(initialValue, trace.named("state"))
 * ```
 */
public expect fun TraceBase.named(name: String): TraceBase

/**
 * The default trace string formatter.
 *
 * On JVM when `kotlinx.atomicfu.trace.thread` system property is set, then the default format
 * also includes thread name for each operation.
 */
public expect val traceFormatDefault: TraceFormat

/**
 * Base class for implementations of `Trace`.
 */
@JsName(TRACE_BASE_CONSTRUCTOR)
public open class TraceBase internal constructor() {
    /**
     * Accepts the logging [event] and appends it to the trace.
     */
    @JsName(TRACE_APPEND_1)
    public open fun append(event: Any) {}

    /**
     * Accepts the logging events [event1], [event2] and appends them to the trace.
     */
    @JsName(TRACE_APPEND_2)
    public open fun append(event1: Any, event2: Any) {}

    /**
     * Accepts the logging events [event1], [event2], [event3] and appends them to the trace.
     */
    @JsName(TRACE_APPEND_3)
    public open fun append(event1: Any, event2: Any, event3: Any) {}

    /**
     * Accepts the logging events [event1], [event2], [event3], [event4] and appends them to the trace.
     */
    @JsName(TRACE_APPEND_4)
    public open fun append(event1: Any, event2: Any, event3: Any, event4: Any) {}

    /**
     * Accepts the logging [event] and appends it to the trace.
     */
    @InlineOnly
    public inline operator fun invoke(event: () -> Any) {
        append(event())
    }

    /**
     * NOP tracing.
     */
    public object None : TraceBase()
}