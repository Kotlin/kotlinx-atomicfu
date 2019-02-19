/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package internal_test2

import kotlinx.atomicfu.*

class Updater {
    internal val internalTrace = Trace(format = TraceFormat { i, text -> "Updater: $i [$text]" })
    private val t = Trace(20)

    val a1 = atomic(5, internalTrace)
    private val a2 = atomic(6, t)
}