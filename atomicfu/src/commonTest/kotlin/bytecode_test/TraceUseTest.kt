/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package bytecode_test

import kotlinx.atomicfu.*
import kotlin.test.*

class TraceUseTest {
    // todo: figure out what to do with trace format (removing it is not supported right now)
    val trace = Trace(size = 64)
    val current = atomic(0, trace.named("current"))

    @Test
    fun testTraceUse() {
        assertEquals(0, update(42))
        assertEquals(42, current.value)
    }

    fun update(x: Int): Int {
        // custom trace message
        trace { "calling update($x)" }
        // automatic tracing of modification operations
        return current.getAndAdd(x)
    }
}