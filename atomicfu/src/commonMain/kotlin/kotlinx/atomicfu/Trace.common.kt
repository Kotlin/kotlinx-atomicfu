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
@JsName("atomicfu\$Trace\$")
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
 * The default trade string formatter.
 *
 * On JVM when `kotlinx.atomicfu.trace.thread` system property is set, then the default format
 * also includes thread name for each operation.
 */
public expect val traceFormatDefault: TraceFormat

@JsName("atomicfu\$TraceBase\$")
public open class TraceBase internal constructor() {
    @JsName("atomicfu\$Trace\$append\$")
    @PublishedApi
    internal open fun append(text: String) {}

    @InlineOnly
    public inline operator fun invoke(text: () -> String) {
        append(text())
    }

    /**
     * NOP tracing.
     */
    public object None : TraceBase()
}