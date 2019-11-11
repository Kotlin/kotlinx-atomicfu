@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kotlinx.atomicfu.locks

import kotlin.internal.InlineOnly

public actual typealias SynchronizedObject = Any

@InlineOnly
public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T =
    kotlin.synchronized(lock, block)