/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("AtomicFU")
@file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier")

package kotlinx.atomicfu

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.reflect.KProperty
import kotlinx.atomicfu.TraceBase.None

/**
 * Creates atomic reference with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic<Type>(initial)
 * ```
 */
public actual fun <T> atomic(initial: T, trace: TraceBase): AtomicRef<T> = AtomicRef<T>(initial, trace)

public actual fun <T> atomic(initial: T): AtomicRef<T> = atomic(initial, None)

/**
 * Creates atomic [Int] with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic(initialInt)
 * ```
 */
public actual fun atomic(initial: Int, trace: TraceBase): AtomicInt = AtomicInt(initial, trace)

public actual fun atomic(initial: Int): AtomicInt = atomic(initial, None)

/**
 * Creates atomic [Long] with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic(initialLong)
 * ```
 */
public actual fun atomic(initial: Long, trace: TraceBase): AtomicLong = AtomicLong(initial, trace)

public actual fun atomic(initial: Long): AtomicLong = atomic(initial, None)

/**
 * Creates atomic [Boolean] with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic(initialBoolean)
 * ```
 */
public actual fun atomic(initial: Boolean, trace: TraceBase): AtomicBoolean = AtomicBoolean(initial, trace)

public actual fun atomic(initial: Boolean): AtomicBoolean = atomic(initial, None)

// ==================================== AtomicRef ====================================

/**
 * Atomic reference to a variable of type [T] with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
@Suppress("UNCHECKED_CAST")
public actual class AtomicRef<T> internal constructor(value: T, val trace: TraceBase) {
    /**
     * Reading/writing this property maps to read/write of volatile variable.
     */
    @Volatile
    public actual var value: T = value
        set(value) {
            interceptor.beforeUpdate(this)
            field = value
            if (trace !== TraceBase.None) trace { "set($value)" }
            interceptor.afterSet(this, value)
        }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { this.value = value }

    /**
     * Maps to [AtomicReferenceFieldUpdater.lazySet].
     */
    public actual fun lazySet(value: T) {
        interceptor.beforeUpdate(this)
        FU.lazySet(this, value)
        if (trace !== TraceBase.None) trace { "lazySet($value)" }
        interceptor.afterSet(this, value)
    }

    /**
     * Maps to [AtomicReferenceFieldUpdater.compareAndSet].
     */
    public actual fun compareAndSet(expect: T, update: T): Boolean {
        interceptor.beforeUpdate(this)
        val result = FU.compareAndSet(this, expect, update)
        if (result) {
            if (trace !== TraceBase.None) trace { "CAS($expect, $update)" }
            interceptor.afterRMW(this, expect, update)
        }
        return result
    }

    /**
     * Maps to [AtomicReferenceFieldUpdater.getAndSet].
     */
    public actual fun getAndSet(value: T): T {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndSet(this, value) as T
        if (trace !== TraceBase.None) trace { "getAndSet($value):$oldValue" }
        interceptor.afterRMW(this, oldValue, value)
        return oldValue
    }

    override fun toString(): String = value.toString()

    private companion object {
        private val FU = AtomicReferenceFieldUpdater.newUpdater(AtomicRef::class.java, Any::class.java, "value")
    }
}


// ==================================== AtomicBoolean ====================================

/**
 * Atomic reference to an [Boolean] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
@Suppress("UNCHECKED_CAST")
public actual class AtomicBoolean internal constructor(v: Boolean, val trace: TraceBase) {

    @Volatile
    private var _value: Int = if (v) 1 else 0

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { this.value = value }

    /**
     * Reading/writing this property maps to read/write of volatile variable.
     */
    public actual var value: Boolean
        get() = _value != 0
        set(value) {
            interceptor.beforeUpdate(this)
            _value = if (value) 1 else 0
            if (trace !== TraceBase.None) trace { "set($value)" }
            interceptor.afterSet(this, value)
        }

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public actual fun lazySet(value: Boolean) {
        interceptor.beforeUpdate(this)
        val v = if (value) 1 else 0
        FU.lazySet(this, v)
        if (trace !== TraceBase.None) trace { "lazySet($value)" }
        interceptor.afterSet(this, value)
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        interceptor.beforeUpdate(this)
        val e = if (expect) 1 else 0
        val u = if (update) 1 else 0
        val result = FU.compareAndSet(this, e, u)
        if (result) {
            if (trace !== TraceBase.None) trace { "CAS($expect, $update)" }
            interceptor.afterRMW(this, expect, update)
        }
        return result
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public actual fun getAndSet(value: Boolean): Boolean {
        interceptor.beforeUpdate(this)
        val v = if (value) 1 else 0
        val oldValue = FU.getAndSet(this, v)
        if (trace !== TraceBase.None) trace { "getAndSet($value):$oldValue" }
        interceptor.afterRMW(this, (oldValue == 1), value)
        return oldValue == 1
    }

    override fun toString(): String = value.toString()

    private companion object {
        private val FU = AtomicIntegerFieldUpdater.newUpdater(AtomicBoolean::class.java, "_value")
    }
}

// ==================================== AtomicInt ====================================

/**
 * Atomic reference to an [Int] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
public actual class AtomicInt internal constructor(value: Int, val trace: TraceBase) {
    /**
     * Reads/writes of this property maps to read/write of volatile variable.
     */
    @Volatile
    public actual var value: Int = value
        set(value) {
            interceptor.beforeUpdate(this)
            field = value
            if (trace !== TraceBase.None) trace { "set($value)" }
            interceptor.afterSet(this, value)
        }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { this.value = value }

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public actual fun lazySet(value: Int) {
        interceptor.beforeUpdate(this)
        FU.lazySet(this, value)
        if (trace !== TraceBase.None) trace { "lazySet($value)" }
        interceptor.afterSet(this, value)
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public actual fun compareAndSet(expect: Int, update: Int): Boolean {
        interceptor.beforeUpdate(this)
        val result = FU.compareAndSet(this, expect, update)
        if (result) {
            if (trace !== TraceBase.None) trace { "CAS($expect, $update)" }
            interceptor.afterRMW(this, expect, update)
        }
        return result
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public actual fun getAndSet(value: Int): Int {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndSet(this, value)
        if (trace !== TraceBase.None) trace { "getAndSet($value):$oldValue" }
        interceptor.afterRMW(this, oldValue, value)
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndIncrement].
     */
    public actual fun getAndIncrement(): Int {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndIncrement(this)
        if (trace !== TraceBase.None) trace { "getAndInc():$oldValue" }
        interceptor.afterRMW(this, oldValue, oldValue + 1)
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndDecrement].
     */
    public actual fun getAndDecrement(): Int {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndDecrement(this)
        if (trace !== TraceBase.None) trace { "getAndDec():$oldValue" }
        interceptor.afterRMW(this, oldValue, oldValue - 1)
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndAdd].
     */
    public actual fun getAndAdd(delta: Int): Int {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndAdd(this, delta)
        if (trace !== TraceBase.None) trace { "getAndAdd($delta):$oldValue" }
        interceptor.afterRMW(this, oldValue, oldValue + delta)
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.addAndGet].
     */
    public actual fun addAndGet(delta: Int): Int {
        interceptor.beforeUpdate(this)
        val newValue = FU.addAndGet(this, delta)
        if (trace !== TraceBase.None) trace { "addAndGet($delta):$newValue" }
        interceptor.afterRMW(this, newValue - delta, newValue)
        return newValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.incrementAndGet].
     */
    public actual fun incrementAndGet(): Int {
        interceptor.beforeUpdate(this)
        val newValue = FU.incrementAndGet(this)
        if (trace !== TraceBase.None) trace { "incAndGet():$newValue" }
        interceptor.afterRMW(this, newValue - 1, newValue)
        return newValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.decrementAndGet].
     */
    public actual fun decrementAndGet(): Int {
        interceptor.beforeUpdate(this)
        val newValue = FU.decrementAndGet(this)
        if (trace !== TraceBase.None) trace { "decAndGet():$newValue" }
        interceptor.afterRMW(this, newValue + 1, newValue)
        return newValue
    }

    /**
     * Performs atomic addition of [delta].
     */
    public actual inline operator fun plusAssign(delta: Int) {
        getAndAdd(delta)
    }

    /**
     * Performs atomic subtraction of [delta].
     */
    public actual inline operator fun minusAssign(delta: Int) {
        getAndAdd(-delta)
    }

    override fun toString(): String = value.toString()

    private companion object {
        private val FU = AtomicIntegerFieldUpdater.newUpdater(AtomicInt::class.java, "value")
    }
}

// ==================================== AtomicLong ====================================

/**
 * Atomic reference to a [Long] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
public actual class AtomicLong internal constructor(value: Long, val trace: TraceBase) {
    /**
     * Reads/writes of this property maps to read/write of volatile variable.
     */
    @Volatile
    public actual var value: Long = value
        set(value) {
            interceptor.beforeUpdate(this)
            field = value
            if (trace !== TraceBase.None) trace { "set($value)" }
            interceptor.afterSet(this, value)
        }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { this.value = value }

    /**
     * Maps to [AtomicLongFieldUpdater.lazySet].
     */
    public actual fun lazySet(value: Long) {
        interceptor.beforeUpdate(this)
        FU.lazySet(this, value)
        if (trace !== TraceBase.None) trace { "lazySet($value)" }
        interceptor.afterSet(this, value)
    }

    /**
     * Maps to [AtomicLongFieldUpdater.compareAndSet].
     */
    public actual fun compareAndSet(expect: Long, update: Long): Boolean {
        interceptor.beforeUpdate(this)
        val result = FU.compareAndSet(this, expect, update)
        if (result) {
            if (trace !== TraceBase.None) trace { "CAS($expect, $update)" }
            interceptor.afterRMW(this, expect, update)
        }
        return result
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndSet].
     */
    public actual fun getAndSet(value: Long): Long {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndSet(this, value)
        if (trace !== TraceBase.None) trace { "getAndSet($value):$oldValue" }
        interceptor.afterRMW(this, oldValue, value)
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndIncrement].
     */
    public actual fun getAndIncrement(): Long {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndIncrement(this)
        if (trace !== TraceBase.None) trace { "getAndInc():$oldValue" }
        interceptor.afterRMW(this, oldValue, oldValue + 1)
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndDecrement].
     */
    public actual fun getAndDecrement(): Long {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndDecrement(this)
        if (trace !== TraceBase.None) trace { "getAndDec():$oldValue" }
        interceptor.afterRMW(this, oldValue, oldValue - 1)
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndAdd].
     */
    public actual fun getAndAdd(delta: Long): Long {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndAdd(this, delta)
        if (trace !== TraceBase.None) trace { "getAndAdd($delta):$oldValue" }
        interceptor.afterRMW(this, oldValue, oldValue + delta)
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.addAndGet].
     */
    public actual fun addAndGet(delta: Long): Long {
        interceptor.beforeUpdate(this)
        val newValue = FU.addAndGet(this, delta)
        if (trace !== TraceBase.None) trace { "addAndGet($delta):$newValue" }
        interceptor.afterRMW(this, newValue - delta, newValue)
        return newValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.incrementAndGet].
     */
    public actual fun incrementAndGet(): Long {
        interceptor.beforeUpdate(this)
        val newValue = FU.incrementAndGet(this)
        if (trace !== TraceBase.None) trace { "incAndGet():$newValue" }
        interceptor.afterRMW(this, newValue - 1, newValue)
        return newValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.decrementAndGet].
     */
    public actual fun decrementAndGet(): Long {
        interceptor.beforeUpdate(this)
        val newValue = FU.decrementAndGet(this)
        if (trace !== TraceBase.None) trace { "decAndGet():$newValue" }
        interceptor.afterRMW(this, newValue + 1, newValue)
        return newValue
    }

    /**
     * Performs atomic addition of [delta].
     */
    public actual inline operator fun plusAssign(delta: Long) {
        getAndAdd(delta)
    }

    /**
     * Performs atomic subtraction of [delta].
     */
    public actual inline operator fun minusAssign(delta: Long) {
        getAndAdd(-delta)
    }

    override fun toString(): String = value.toString()

    private companion object {
        private val FU = AtomicLongFieldUpdater.newUpdater(AtomicLong::class.java, "value")
    }
}