@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kotlinx.atomicfu.locks

import kotlin.internal.InlineOnly

public actual typealias SynchronizedObject = Any

@InlineOnly
public actual inline fun reentrantLock() = ReentrantLock()

public actual typealias ReentrantLock = java.util.concurrent.locks.ReentrantLock

@InlineOnly
public actual inline fun <T> ReentrantLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

@InlineOnly
public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T =
    kotlin.synchronized(lock, block)