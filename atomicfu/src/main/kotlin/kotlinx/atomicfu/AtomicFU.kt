@file:JvmName("AtomicFU")
@file:Suppress("NOTHING_TO_INLINE")

package kotlinx.atomicfu

import java.util.concurrent.atomic.*

fun atomicInt(): AtomicInt = AtomicInt(0)
fun atomicInt(initial: Int): AtomicInt = AtomicInt(initial)
fun atomicLong(): AtomicLong = AtomicLong(0L)
fun atomicLong(initial: Long): AtomicLong = AtomicLong(initial)
fun <T> atomic(): AtomicRef<T?> = AtomicRef<T?>(null)
fun <T> atomic(initial: T): AtomicRef<T> = AtomicRef<T>(initial)

class AtomicInt internal constructor(initial: Int) {
    /** Get/set of this property maps to read/write of volatile variable */
    @Volatile
    var value: Int = initial

    /** Maps to [AtomicIntegerFieldUpdater.lazySet] */
    fun lazySet(newValue: Int) = FU.lazySet(this, newValue)

    /** Maps to [AtomicIntegerFieldUpdater.compareAndSet] */
    fun compareAndSet(expect: Int, update: Int): Boolean = FU.compareAndSet(this, expect, update)

    /** Maps to [AtomicIntegerFieldUpdater.getAndSet] */
    fun getAndSet(newValue: Int): Int = FU.getAndSet(this, newValue)

    /** Maps to [AtomicIntegerFieldUpdater.getAndIncrement] */
    fun getAndIncrement(): Int = FU.getAndIncrement(this)

    /** Maps to [AtomicIntegerFieldUpdater.getAndDecrement] */
    fun getAndDecrement(): Int = FU.getAndDecrement(this)

    /** Maps to [AtomicIntegerFieldUpdater.getAndAdd] */
    fun getAndAdd(delta: Int): Int = FU.getAndAdd(this, delta)

    /** Maps to [AtomicIntegerFieldUpdater.addAndGet] */
    fun addAndGet(delta: Int): Int = FU.addAndGet(this, delta)

    /** Maps to [AtomicIntegerFieldUpdater.incrementAndGet] */
    fun incrementAndGet(): Int = FU.incrementAndGet(this)

    /** Maps to [AtomicIntegerFieldUpdater.decrementAndGet] */
    fun decrementAndGet(): Int = FU.decrementAndGet(this)

    private companion object {
        private val FU = AtomicIntegerFieldUpdater.newUpdater(AtomicInt::class.java, "value")
    }
}

inline fun AtomicInt.loop(block: (Int) -> Unit): Nothing {
    while (true) {
        block(value)
    }
}

inline fun AtomicInt.update(block: (Int) -> Int) {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return
    }
}

inline fun AtomicInt.getAndUpdate(block: (Int) -> Int): Int {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

inline fun AtomicInt.updateAndGet(block: (Int) -> Int): Int {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}

class AtomicLong internal constructor(initial: Long) {
    /** Get/set of this property maps to read/write of volatile variable */
    @Volatile
    var value: Long = initial

    /** Maps to [AtomicLongFieldUpdater.lazySet] */
    fun lazySet(newValue: Long) = FU.lazySet(this, newValue)

    /** Maps to [AtomicLongFieldUpdater.compareAndSet] */
    fun compareAndSet(expect: Long, update: Long): Boolean = FU.compareAndSet(this, expect, update)

    /** Maps to [AtomicLongFieldUpdater.getAndSet] */
    fun getAndSet(newValue: Long): Long = FU.getAndSet(this, newValue)

    /** Maps to [AtomicLongFieldUpdater.getAndIncrement] */
    fun getAndIncrement(): Long = FU.getAndIncrement(this)

    /** Maps to [AtomicLongFieldUpdater.getAndDecrement] */
    fun getAndDecrement(): Long = FU.getAndDecrement(this)

    /** Maps to [AtomicLongFieldUpdater.getAndAdd] */
    fun getAndAdd(delta: Long): Long = FU.getAndAdd(this, delta)

    /** Maps to [AtomicLongFieldUpdater.addAndGet] */
    fun addAndGet(delta: Long): Long = FU.addAndGet(this, delta)

    /** Maps to [AtomicLongFieldUpdater.incrementAndGet] */
    fun incrementAndGet(): Long = FU.incrementAndGet(this)

    /** Maps to [AtomicLongFieldUpdater.decrementAndGet] */
    fun decrementAndGet(): Long = FU.decrementAndGet(this)

    private companion object {
        private val FU = AtomicLongFieldUpdater.newUpdater(AtomicLong::class.java, "value")
    }
}

inline fun AtomicLong.loop(block: (Long) -> Unit): Nothing {
    while (true) {
        block(value)
    }
}

inline fun AtomicLong.update(block: (Long) -> Long) {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return
    }
}

inline fun AtomicLong.getAndUpdate(block: (Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

inline fun AtomicLong.updateAndGet(block: (Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}

@Suppress("UNCHECKED_CAST")
class AtomicRef<T> internal constructor(initial: T) {
    /** Get/set of this property maps to read/write of volatile variable */
    @Volatile
    var value: T = initial

    /** Maps to [AtomicReferenceFieldUpdater.lazySet] */
    fun lazySet(newValue: T) = FU.lazySet(this, newValue)

    /** Maps to [AtomicReferenceFieldUpdater.compareAndSet] */
    fun compareAndSet(expect: T, update: T): Boolean = FU.compareAndSet(this, expect, update)

    /** Maps to [AtomicReferenceFieldUpdater.getAndSet] */
    fun getAndSet(newValue: T): T = FU.getAndSet(this, newValue) as T

    private companion object {
        private val FU = AtomicReferenceFieldUpdater.newUpdater(AtomicRef::class.java, Any::class.java, "value")
    }
}

inline fun <T> AtomicRef<T>.loop(block: (T) -> Unit): Nothing {
    while (true) {
        block(value)
    }
}

inline fun <T> AtomicRef<T>.update(block: (T) -> T) {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return
    }
}

inline fun <T> AtomicRef<T>.getAndUpdate(block: (T) -> T): T {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

inline fun <T> AtomicRef<T>.updateAndGet(block: (T) -> T): T {
    while (true) {
        val cur = value
        val upd = block(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}
