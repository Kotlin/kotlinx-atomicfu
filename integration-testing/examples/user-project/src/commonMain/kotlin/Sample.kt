/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import examples.mpp_sample.*
import kotlin.test.*

fun doWorld()  {
    val sampleClass = AtomicSampleClass()
    sampleClass.doWork(1234)
    assertEquals(1234, sampleClass.x)
    assertEquals(42, sampleClass.synchronizedSetState(42))
}
