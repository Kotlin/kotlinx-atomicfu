/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kotlinx.atomicfu

import kotlin.internal.InlineOnly
import kotlin.js.JsName

/**
 * Trace string formatter.
 */
@JsName(TRACE_FORMAT_CLASS)
public open class TraceFormat {
    /**
     * Formats trace at the given [index] with the given [event] of Any type.
     */
    @JsName(TRACE_FORMAT_FORMAT_FUNCTION)
    public open fun format(index: Int, event: Any): String = "$index: $event"
}

/**
 * Creates trace string formatter with the given [format] code block.
 */
@InlineOnly
public inline fun TraceFormat(crossinline format: (index: Int, event: Any) -> String): TraceFormat =
    object : TraceFormat() {
        override fun format(index: Int, event: Any): String = format(index, event)
    }