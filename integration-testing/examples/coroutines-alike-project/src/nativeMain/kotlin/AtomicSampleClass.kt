/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package examples.mpp_sample

import kotlinx.atomicfu.locks.withLock as withLock2

public typealias SO = kotlinx.atomicfu.locks.SynchronizedObject

public inline fun <T> synchronizedImpl(lock: SO, block: () -> T): T = lock.withLock2(block)
