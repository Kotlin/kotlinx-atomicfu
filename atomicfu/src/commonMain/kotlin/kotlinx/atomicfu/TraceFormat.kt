/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu

/**
 * Trace string formatter.
 */
public open class TraceFormat {
    /**
     * Formats trace at the given [index] with the given [text] as string.
     */
    public open fun format(index: Int, text: String): String = "$index: $text"
}

/**
 * Creates trace string formatter with the given [format] code block.
 */
public inline fun TraceFormat(crossinline format: (index: Int, text: String) -> String): TraceFormat =
    object : TraceFormat() {
        override fun format(index: Int, text: String): String = format(index, text)
    }