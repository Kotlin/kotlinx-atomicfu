package kotlinx.atomicfu.locks

import kotlinx.atomicfu.*

@ExperimentalConcurrencyApi
public expect open class SynchronizedObject() // marker abstract class

@ExperimentalConcurrencyApi
public expect fun reentrantLock(): ReentrantLock

@ExperimentalConcurrencyApi
public expect class ReentrantLock {
    fun lock(): Unit
    fun tryLock(): Boolean
    fun unlock(): Unit
}

@ExperimentalConcurrencyApi
public expect inline fun <T> ReentrantLock.withLock(block: () -> T): T

@ExperimentalConcurrencyApi
public expect inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T
