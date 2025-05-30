/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.locks

public expect open class SynchronizedObject() // marker abstract class

public expect fun reentrantLock(): ReentrantLock

public expect class ReentrantLock() {
    public fun lock(): Unit
    public fun tryLock(): Boolean
    public fun unlock(): Unit
}

public expect inline fun <T> ReentrantLock.withLock(block: () -> T): T

public expect inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T
