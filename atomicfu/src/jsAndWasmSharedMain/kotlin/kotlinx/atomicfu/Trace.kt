/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu

@Suppress("FunctionName", "DEPRECATION")
@OptionalJsName(TRACE_FACTORY_FUNCTION)
public actual fun Trace(size: Int, format: TraceFormat): TraceBase = TraceBase.None

@Suppress("DEPRECATION")
@OptionalJsName(TRACE_NAMED)
public actual fun TraceBase.named(name: String): TraceBase = TraceBase.None

public actual val traceFormatDefault: TraceFormat = TraceFormat()
