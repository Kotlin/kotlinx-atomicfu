/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu

@JsName("atomicfu\$Trace\$")
@Suppress("FunctionName")
public actual fun Trace(size: Int, format: TraceFormat): TraceBase = TraceBase.None

@JsName("atomicfu\$Trace\$named\$")
public actual fun TraceBase.named(name: String): TraceBase = TraceBase.None

public actual val traceFormatDefault: TraceFormat = TraceFormat()