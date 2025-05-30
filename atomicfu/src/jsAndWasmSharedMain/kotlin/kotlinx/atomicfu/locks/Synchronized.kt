@file:Suppress("DEPRECATION")

package kotlinx.atomicfu.locks

import kotlinx.atomicfu.OptionalJsName
import kotlinx.atomicfu.REENTRANT_LOCK

@Suppress("ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING")
public actual typealias SynchronizedObject = Any

@OptionalJsName(REENTRANT_LOCK)
public val Lock: ReentrantLock = ReentrantLock()

@Suppress("NOTHING_TO_INLINE")
public actual inline fun reentrantLock(): ReentrantLock = Lock

@Suppress("NOTHING_TO_INLINE")
public actual class ReentrantLock {
    public actual inline fun lock(): Unit {}
    public actual inline fun tryLock(): Boolean = true
    public actual inline fun unlock(): Unit {}
}

public actual inline fun <T> ReentrantLock.withLock(block: () -> T) = block()

public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T = block()
