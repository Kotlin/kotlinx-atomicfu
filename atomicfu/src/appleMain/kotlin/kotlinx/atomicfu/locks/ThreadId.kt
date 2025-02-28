/*
 * Copyright 2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.locks

import kotlinx.cinterop.toLong
import platform.posix.pthread_self

actual fun createThreadId() = pthread_self().toLong()