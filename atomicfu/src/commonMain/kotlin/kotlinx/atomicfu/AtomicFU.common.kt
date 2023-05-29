/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package kotlinx.atomicfu

import kotlin.js.JsName
import kotlin.internal.InlineOnly
import kotlinx.atomicfu.TraceBase.None
import kotlin.reflect.KProperty

/**
 * Creates atomic reference with a given [initial] value and a [trace] object to [trace modifications][Trace] of the value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic<Type>(initial, trace)
 * ```
 */
public expect fun <T> atomic(initial: T, trace: TraceBase = None): AtomicRef<T>

// Binary compatibility with IR, should be removed with Kotlin 1.5 release

/**
 * Creates atomic reference with a given [initial] value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic<Type>(initial)
 * ```
 */
public expect fun <T> atomic(initial: T): AtomicRef<T>

/**
 * Creates atomic [Int] with a given [initial] value and a [trace] object to [trace modifications][Trace] of the value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialInt, trace)
 * ```
 */
public expect fun atomic(initial: Int, trace: TraceBase = None): AtomicInt

// Binary compatibility with IR, should be removed with Kotlin 1.5 release

/**
 * Creates atomic [Int] with a given [initial] value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialInt)
 * ```
 */
public expect fun atomic(initial: Int): AtomicInt

/**
 * Creates atomic [UInt] with a given [initial] value and a [trace] object to [trace modifications][Trace] of the value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialUInt, trace)
 * ```
 */
@OptIn(ExperimentalUnsignedTypes::class)
public expect fun atomic(initial: UInt, trace: TraceBase = None): AtomicUInt

/**
 * Creates atomic [UInt] with a given [initial] value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialInt)
 * ```
 */
@OptIn(ExperimentalUnsignedTypes::class)
public expect fun atomic(initial: UInt): AtomicUInt

/**
 * Creates atomic [Long] with a given [initial] value and a [trace] object to [trace modifications][Trace] of the value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialLong, trace)
 * ```
 */
public expect fun atomic(initial: Long, trace: TraceBase = None): AtomicLong

// Binary compatibility with IR, should be removed with Kotlin 1.5 release

/**
 * Creates atomic [Long] with a given [initial] value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialLong)
 * ```
 */
public expect fun atomic(initial: Long): AtomicLong

/**
 * Creates atomic [ULong] with a given [initial] value and a [trace] object to [trace modifications][Trace] of the value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialUInt, trace)
 * ```
 */
@OptIn(ExperimentalUnsignedTypes::class)
public expect fun atomic(initial: ULong, trace: TraceBase = None): AtomicULong

/**
 * Creates atomic [ULong] with a given [initial] value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialInt)
 * ```
 */
@OptIn(ExperimentalUnsignedTypes::class)
public expect fun atomic(initial: ULong): AtomicULong

/**
 * Creates atomic [Boolean] with a given [initial] value and a [trace] object to [trace modifications][Trace] of the value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialBoolean, trace)
 * ```
 */
public expect fun atomic(initial: Boolean, trace: TraceBase = None): AtomicBoolean

// Binary compatibility with IR, should be removed with Kotlin 1.5 release

/**
 * Creates atomic [Boolean] with a given [initial] value.
 *
 * It can only be used to initialize a private or internal read-only property, like this:
 *
 * ```
 * private val f = atomic(initialBoolean)
 * ```
 */
public expect fun atomic(initial: Boolean): AtomicBoolean

/**
 * Creates array of AtomicRef<T> of specified size, where each element is initialised with null value
 */
@JsName(ATOMIC_ARRAY_OF_NULLS)
public fun <T> atomicArrayOfNulls(size: Int): AtomicArray<T?> = AtomicArray(size)

// ==================================== AtomicRef ====================================

/**
 * Atomic reference to a variable of type [T] with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
public expect class AtomicRef<T> {
    /**
     * Reading/writing this property maps to read/write of volatile variable.
     */
    public var value: T

    @InlineOnly
    public inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T

    @InlineOnly
    public inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)

    /**
     * Maps to [AtomicReferenceFieldUpdater.lazySet].
     */
    public fun lazySet(value: T)

    /**
     * Maps to [AtomicReferenceFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: T, update: T): Boolean

    /**
     * Maps to [AtomicReferenceFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: T): T
}

/**
 * Infinite loop that reads this atomic variable and performs the specified [action] on its value.
 */
public inline fun <T> AtomicRef<T>.loop(action: (T) -> Unit): Nothing {
    while (true) {
        action(value)
    }
}

/**
 * Updates variable atomically using the specified [function] of its value.
 */
public inline fun <T> AtomicRef<T>.update(function: (T) -> T) {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its old value.
 */
public inline fun <T> AtomicRef<T>.getAndUpdate(function: (T) -> T): T {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its new value.
 */
public inline fun <T> AtomicRef<T>.updateAndGet(function: (T) -> T): T {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}


// ==================================== AtomicBoolean ====================================

/**
 * Atomic reference to a [Boolean] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
public expect class AtomicBoolean {
    /**
     * Reading/writing this property maps to read/write of volatile variable.
     */
    public var value: Boolean

    @InlineOnly
    public inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean

    @InlineOnly
    public inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean)

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public fun lazySet(value: Boolean)

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: Boolean, update: Boolean): Boolean

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: Boolean): Boolean

}

/**
 * Infinite loop that reads this atomic variable and performs the specified [action] on its value.
 */
public inline fun AtomicBoolean.loop(action: (Boolean) -> Unit): Nothing {
    while (true) {
        action(value)
    }
}

/**
 * Updates variable atomically using the specified [function] of its value.
 */
public inline fun AtomicBoolean.update(function: (Boolean) -> Boolean) {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its old value.
 */
public inline fun AtomicBoolean.getAndUpdate(function: (Boolean) -> Boolean): Boolean {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its new value.
 */
public inline fun AtomicBoolean.updateAndGet(function: (Boolean) -> Boolean): Boolean {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}

// ==================================== AtomicInt ====================================

/**
 * Atomic reference to an [Int] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
public expect class AtomicInt {
    /**
     * Reads/writes of this property maps to read/write of volatile variable.
     */
    public var value: Int

    @InlineOnly
    public inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Int

    @InlineOnly
    public inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int)

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public fun lazySet(value: Int)

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: Int, update: Int): Boolean

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: Int): Int

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndIncrement].
     */
    public fun getAndIncrement(): Int

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndDecrement].
     */
    public fun getAndDecrement(): Int

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndAdd].
     */
    public fun getAndAdd(delta: Int): Int

    /**
     * Maps to [AtomicIntegerFieldUpdater.addAndGet].
     */
    public fun addAndGet(delta: Int): Int

    /**
     * Maps to [AtomicIntegerFieldUpdater.incrementAndGet].
     */
    public fun incrementAndGet(): Int

    /**
     * Maps to [AtomicIntegerFieldUpdater.decrementAndGet].
     */
    public fun decrementAndGet(): Int

    /**
     * Performs atomic addition of [delta].
     */
    public inline operator fun plusAssign(delta: Int)

    /**
     * Performs atomic subtraction of [delta].
     */
    public inline operator fun minusAssign(delta: Int)
}

/**
 * Infinite loop that reads this atomic variable and performs the specified [action] on its value.
 */
public inline fun AtomicInt.loop(action: (Int) -> Unit): Nothing {
    while (true) {
        action(value)
    }
}

/**
 * Updates variable atomically using the specified [function] of its value.
 */
public inline fun AtomicInt.update(function: (Int) -> Int) {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its old value.
 */
public inline fun AtomicInt.getAndUpdate(function: (Int) -> Int): Int {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its new value.
 */
public inline fun AtomicInt.updateAndGet(function: (Int) -> Int): Int {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}

// ==================================== AtomicUInt ====================================

/**
 * Atomic reference to an [UInt] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public expect class AtomicUInt {
    /**
     * Reads/writes of this property maps to read/write of volatile variable.
     */
    public var value: UInt

    @InlineOnly
    public inline operator fun getValue(thisRef: Any?, property: KProperty<*>): UInt

    @InlineOnly
    public inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: UInt)

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public fun lazySet(value: UInt)

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: UInt, update: UInt): Boolean

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: UInt): UInt

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndIncrement].
     */
    public fun getAndIncrement(): UInt

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndDecrement].
     */
    public fun getAndDecrement(): UInt

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndAdd].
     */
    public fun getAndAdd(delta: UInt): UInt


    /**
     * Maps to [AtomicIntegerFieldUpdater.addAndGet].
     */
    public fun addAndGet(delta: UInt): UInt

    /**
     * Atomically subtracts the given value to the current value
     * of the field of the given object managed by this updater.
     */
    public fun getAndSub(delta: UInt): UInt

    /**
     * Atomically subtracts the given value to the current value
     * of the field of the given object managed by this updater.
     */
    public fun subAndGet(delta: UInt): UInt

    /**
     * Maps to [AtomicIntegerFieldUpdater.incrementAndGet].
     */
    public fun incrementAndGet(): UInt

    /**
     * Maps to [AtomicIntegerFieldUpdater.decrementAndGet].
     */
    public fun decrementAndGet(): UInt

    /**
     * Performs atomic addition of [delta].
     */
    public inline operator fun plusAssign(delta: UInt)

    /**
     * Performs atomic subtraction of [delta].
     */
    public inline operator fun minusAssign(delta: UInt)
}

/**
 * Infinite loop that reads this atomic variable and performs the specified [action] on its value.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun AtomicUInt.loop(action: (UInt) -> Unit): Nothing {
    while (true) {
        action(value)
    }
}

/**
 * Updates variable atomically using the specified [function] of its value.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun AtomicUInt.update(function: (UInt) -> UInt) {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its old value.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun AtomicUInt.getAndUpdate(function: (UInt) -> UInt): UInt {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its new value.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun AtomicUInt.updateAndGet(function: (UInt) -> UInt): UInt {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}

// ==================================== AtomicLong ====================================

/**
 * Atomic reference to a [Long] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
public expect class AtomicLong {
    /**
     * Reads/writes of this property maps to read/write of volatile variable.
     */
    public var value: Long

    @InlineOnly
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): Long

    @InlineOnly
    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long)

    /**
     * Maps to [AtomicLongFieldUpdater.lazySet].
     */
    public fun lazySet(value: Long)

    /**
     * Maps to [AtomicLongFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: Long, update: Long): Boolean

    /**
     * Maps to [AtomicLongFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: Long): Long

    /**
     * Maps to [AtomicLongFieldUpdater.getAndIncrement].
     */
    public fun getAndIncrement(): Long

    /**
     * Maps to [AtomicLongFieldUpdater.getAndDecrement].
     */
    public fun getAndDecrement(): Long

    /**
     * Maps to [AtomicLongFieldUpdater.getAndAdd].
     */
    public fun getAndAdd(delta: Long): Long

    /**
     * Maps to [AtomicLongFieldUpdater.addAndGet].
     */
    public fun addAndGet(delta: Long): Long

    /**
     * Maps to [AtomicLongFieldUpdater.incrementAndGet].
     */
    public fun incrementAndGet(): Long

    /**
     * Maps to [AtomicLongFieldUpdater.decrementAndGet].
     */
    public fun decrementAndGet(): Long

    /**
     * Performs atomic addition of [delta].
     */
    public inline operator fun plusAssign(delta: Long)

    /**
     * Performs atomic subtraction of [delta].
     */
    public inline operator fun minusAssign(delta: Long)
}

/**
 * Infinite loop that reads this atomic variable and performs the specified [action] on its value.
 */
public inline fun AtomicLong.loop(action: (Long) -> Unit): Nothing {
    while (true) {
        action(value)
    }
}

/**
 * Updates variable atomically using the specified [function] of its value.
 */
public inline fun AtomicLong.update(function: (Long) -> Long) {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its old value.
 */
public inline fun AtomicLong.getAndUpdate(function: (Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its new value.
 */
public inline fun AtomicLong.updateAndGet(function: (Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}

// ==================================== AtomicULong ====================================

/**
 * Atomic reference to an [ULong] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public expect class AtomicULong {
    /**
     * Reads/writes of this property maps to read/write of volatile variable.
     */
    public var value: ULong

    @InlineOnly
    public inline operator fun getValue(thisRef: Any?, property: KProperty<*>): ULong

    @InlineOnly
    public inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: ULong)

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public fun lazySet(value: ULong)

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: ULong, update: ULong): Boolean

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: ULong): ULong

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndIncrement].
     */
    public fun getAndIncrement(): ULong

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndDecrement].
     */
    public fun getAndDecrement(): ULong

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndAdd].
     */
    public fun getAndAdd(delta: ULong): ULong


    /**
     * Maps to [AtomicIntegerFieldUpdater.addAndGet].
     */
    public fun addAndGet(delta: ULong): ULong

    /**
     * Atomically subtracts the given value to the current value
     * of the field of the given object managed by this updater.
     */
    public fun getAndSub(delta: ULong): ULong

    /**
     * Atomically subtracts the given value to the current value
     * of the field of the given object managed by this updater.
     */
    public fun subAndGet(delta: ULong): ULong

    /**
     * Maps to [AtomicIntegerFieldUpdater.incrementAndGet].
     */
    public fun incrementAndGet(): ULong

    /**
     * Maps to [AtomicIntegerFieldUpdater.decrementAndGet].
     */
    public fun decrementAndGet(): ULong

    /**
     * Performs atomic addition of [delta].
     */
    public inline operator fun plusAssign(delta: ULong)

    /**
     * Performs atomic subtraction of [delta].
     */
    public inline operator fun minusAssign(delta: ULong)
}

/**
 * Infinite loop that reads this atomic variable and performs the specified [action] on its value.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun AtomicULong.loop(action: (ULong) -> Unit): Nothing {
    while (true) {
        action(value)
    }
}

/**
 * Updates variable atomically using the specified [function] of its value.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun AtomicULong.update(function: (ULong) -> ULong) {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its old value.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun AtomicULong.getAndUpdate(function: (ULong) -> ULong): ULong {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return cur
    }
}

/**
 * Updates variable atomically using the specified [function] of its value and returns its new value.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun AtomicULong.updateAndGet(function: (ULong) -> ULong): ULong {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}

// ==================================== AtomicIntArray ====================================

/**
 * Creates a new array of AtomicInt values of the specified size, where each element is initialised with 0
 */
@JsName(ATOMIC_INT_ARRAY)
public class AtomicIntArray(size: Int) {
    private val array = Array(size) { atomic(0) }

    @JsName(ARRAY_SIZE)
    public val size: Int
        get() = array.size

    @JsName(ARRAY_ELEMENT_GET)
    public operator fun get(index: Int): AtomicInt = array[index]
}

// ==================================== AtomicLongArray ====================================

/**
 * Creates a new array of AtomicLong values of the specified size, where each element is initialised with 0L
 */
@JsName(ATOMIC_LONG_ARRAY)
public class AtomicLongArray(size: Int) {
    private val array = Array(size) { atomic(0L) }

    @JsName(ARRAY_SIZE)
    public val size: Int
        get() = array.size

    @JsName(ARRAY_ELEMENT_GET)
    public operator fun get(index: Int): AtomicLong = array[index]
}

// ==================================== AtomicIntArray ====================================

/**
 * Creates a new array of AtomicInt values of the specified size, where each element is initialised with 0
 */
@JsName(ATOMIC_UINT_ARRAY)
@OptIn(ExperimentalUnsignedTypes::class)
public class AtomicUIntArray(size: Int) {
    private val array = Array(size) { atomic(0u) }

    @JsName(ARRAY_SIZE)
    public val size: Int
        get() = array.size

    @JsName(ARRAY_ELEMENT_GET)
    public operator fun get(index: Int): AtomicUInt = array[index]
}

// ==================================== AtomicLongArray ====================================

/**
 * Creates a new array of AtomicLong values of the specified size, where each element is initialised with 0L
 */
@JsName(ATOMIC_ULONG_ARRAY)
@OptIn(ExperimentalUnsignedTypes::class)
public class AtomicULongArray(size: Int) {
    private val array = Array(size) { atomic(0UL) }

    @JsName(ARRAY_SIZE)
    public val size: Int
        get() = array.size

    @JsName(ARRAY_ELEMENT_GET)
    public operator fun get(index: Int): AtomicULong = array[index]
}

// ==================================== AtomicBooleanArray ====================================

/**
 * Creates a new array of AtomicBoolean values of the specified size, where each element is initialised with false
 */
@JsName(ATOMIC_BOOLEAN_ARRAY)
public class AtomicBooleanArray(size: Int) {
    private val array = Array(size) { atomic(false) }

    @JsName(ARRAY_SIZE)
    public val size: Int
        get() = array.size

    @JsName(ARRAY_ELEMENT_GET)
    public operator fun get(index: Int): AtomicBoolean = array[index]
}


// ==================================== AtomicArray ====================================

@JsName(ATOMIC_REF_ARRAY)
public class AtomicArray<T> internal constructor(size: Int) {
    private val array = Array(size) { atomic<T?>(null) }

    @JsName(ARRAY_SIZE)
    public val size: Int
        get() = array.size

    @JsName(ARRAY_ELEMENT_GET)
    public operator fun get(index: Int): AtomicRef<T?> = array[index]
}
