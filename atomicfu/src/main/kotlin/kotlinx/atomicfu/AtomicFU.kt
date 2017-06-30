@file:JvmName("AtomicFU")
@file:Suppress("NOTHING_TO_INLINE")

package kotlinx.atomicfu

import java.util.concurrent.atomic.*

fun atomicInt(): AtomicInt = error("Use AtomicFU plugin to transform this invocation")
fun atomicLong(): AtomicLong = error("Use AtomicFU plugin to transform this invocation")
fun <T> atomic(initial: T): AtomicRef<T> = error("Use AtomicFU plugin to transform this invocation")

inline fun <T> atomic(): AtomicRef<T?> = atomic<T?>(null)

abstract class AtomicInt private constructor() {
    /** Get/set of this property maps to read/write of volatile variable */
    abstract var value: Int

    /** Maps to [AtomicIntegerFieldUpdater.lazySet] */
    abstract fun lazySet(newValue: Int)

    /** Maps to [AtomicIntegerFieldUpdater.compareAndSet] */
    abstract fun compareAndSet(expect: Int, update: Int): Boolean

    /** Maps to [AtomicIntegerFieldUpdater.getAndSet] */
    abstract fun getAndSet(newValue: Int): Int

    /** Maps to [AtomicIntegerFieldUpdater.getAndIncrement] */
    abstract fun getAndIncrement(): Int

    /** Maps to [AtomicIntegerFieldUpdater.getAndDecrement] */
    abstract fun getAndDecrement(): Int

    /** Maps to [AtomicIntegerFieldUpdater.getAndAdd] */
    abstract fun getAndAdd(delta: Int): Int

    /** Maps to [AtomicIntegerFieldUpdater.addAndGet] */
    abstract fun addAndGet(delta: Int): Int

    /** Maps to [AtomicIntegerFieldUpdater.incrementAndGet] */
    abstract fun incrementAndGet(): Int

    /** Maps to [AtomicIntegerFieldUpdater.decrementAndGet] */
    abstract fun decrementAndGet(): Int
}

inline fun AtomicInt.loop(block: (Int) -> Unit): Nothing {
    while (true) {
        block(value)
    }
}

inline fun AtomicInt.getAndUpdate(block: (Int) -> Int): Int {
    while (true) {
        val cur = value
        val upd = block(value)
        if (compareAndSet(cur, upd)) return cur
    }
}

inline fun AtomicInt.updateAndGet(block: (Int) -> Int): Int {
    while (true) {
        val cur = value
        val upd = block(value)
        if (compareAndSet(cur, upd)) return upd
    }
}

inline fun AtomicInt.getAndAccumulate(block: (Int, Int) -> Int): Int {
    while (true) {
        val cur = value
        val upd = block(cur, value)
        if (compareAndSet(cur, upd)) return cur
    }
}


inline fun AtomicInt.accumulateAndGet(block: (Int, Int) -> Int): Int {
    while (true) {
        val cur = value
        val upd = block(cur, value)
        if (compareAndSet(cur, upd)) return upd
    }
}

abstract class AtomicLong private constructor() {
    /** Get/set of this property maps to read/write of volatile variable */
    abstract var value: Long

    /** Maps to [AtomicLongFieldUpdater.lazySet] */
    abstract fun lazySet(newValue: Long)

    /** Maps to [AtomicLongFieldUpdater.compareAndSet] */
    abstract fun compareAndSet(expect: Long, update: Long): Boolean

    /** Maps to [AtomicLongFieldUpdater.getAndSet] */
    abstract fun getAndSet(newValue: Long): Long

    /** Maps to [AtomicLongFieldUpdater.getAndIncrement] */
    abstract fun getAndIncrement(): Long

    /** Maps to [AtomicLongFieldUpdater.getAndDecrement] */
    abstract fun getAndDecrement(): Long

    /** Maps to [AtomicLongFieldUpdater.getAndAdd] */
    abstract fun getAndAdd(delta: Long): Long

    /** Maps to [AtomicLongFieldUpdater.addAndGet] */
    abstract fun addAndGet(delta: Long): Long

    /** Maps to [AtomicLongFieldUpdater.incrementAndGet] */
    abstract fun incrementAndGet(): Long

    /** Maps to [AtomicLongFieldUpdater.decrementAndGet] */
    abstract fun decrementAndGet(): Long
}

inline fun AtomicLong.loop(block: (Long) -> Unit): Nothing {
    while (true) {
        block(value)
    }
}

inline fun AtomicLong.getAndUpdate(block: (Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = block(value)
        if (compareAndSet(cur, upd)) return cur
    }
}

inline fun AtomicLong.updateAndGet(block: (Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = block(value)
        if (compareAndSet(cur, upd)) return upd
    }
}

inline fun AtomicLong.getAndAccumulate(block: (Long, Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = block(cur, value)
        if (compareAndSet(cur, upd)) return cur
    }
}

inline fun AtomicLong.accumulateAndGet(block: (Long, Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = block(cur, value)
        if (compareAndSet(cur, upd)) return upd
    }
}

abstract class AtomicRef<T> private constructor() {
    /** Get/set of this property maps to read/write of volatile variable */
    abstract var value: T

    /** Maps to [AtomicReferenceFieldUpdater.lazySet] */
    abstract fun lazySet(newValue: T)

    /** Maps to [AtomicReferenceFieldUpdater.compareAndSet] */
    abstract fun compareAndSet(expect: T, update: T): Boolean

    /** Maps to [AtomicReferenceFieldUpdater.getAndSet] */
    abstract fun getAndSet(newValue: T): T
}

inline fun <T> AtomicRef<T>.loop(block: (T) -> Unit): Nothing {
    while (true) {
        block(value)
    }
}

inline fun <T> AtomicRef<T>.getAndUpdate(block: (T) -> T): T {
    while (true) {
        val cur = value
        val upd = block(value)
        if (compareAndSet(cur, upd)) return cur
    }
}

inline fun <T> AtomicRef<T>.updateAndGet(block: (T) -> T): T {
    while (true) {
        val cur = value
        val upd = block(value)
        if (compareAndSet(cur, upd)) return upd
    }
}

inline fun <T> AtomicRef<T>.getAndAccumulate(block: (T, T) -> T): T {
    while (true) {
        val cur = value
        val upd = block(cur, value)
        if (compareAndSet(cur, upd)) return cur
    }
}


inline fun <T> AtomicRef<T>.accumulateAndGet(block: (T, T) -> T): T {
    while (true) {
        val cur = value
        val upd = block(cur, value)
        if (compareAndSet(cur, upd)) return upd
    }
}


