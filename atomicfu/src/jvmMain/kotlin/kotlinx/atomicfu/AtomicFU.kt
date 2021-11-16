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
            field = value
            if (trace !== None) trace { "set($value)" }
        }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { this.value = value }

    /**
     * Maps to [AtomicReferenceFieldUpdater.lazySet].
     */
    public actual fun lazySet(value: T) {
        FU.lazySet(this, value)
        if (trace !== None) trace { "lazySet($value)" }
    }

    /**
     * Maps to [AtomicReferenceFieldUpdater.compareAndSet].
     */
    public actual fun compareAndSet(expect: T, update: T): Boolean {
        val result = FU.compareAndSet(this, expect, update)
        if (result && trace !== None) trace { "CAS($expect, $update)" }
        return result
    }

    /**
     * Maps to [AtomicReferenceFieldUpdater.getAndSet].
     */
    public actual fun getAndSet(value: T): T {
        val oldValue = FU.getAndSet(this, value) as T
        if (trace !== None) trace { "getAndSet($value):$oldValue" }
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
            _value = if (value) 1 else 0
            if (trace !== None) trace { "set($value)" }
        }

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public actual fun lazySet(value: Boolean) {
        val v = if (value) 1 else 0
        FU.lazySet(this, v)
        if (trace !== None) trace { "lazySet($value)" }
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        val e = if (expect) 1 else 0
        val u = if (update) 1 else 0
        val result = FU.compareAndSet(this, e, u)
        if (result && trace !== None) trace { "CAS($expect, $update)" }
        return result
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public actual fun getAndSet(value: Boolean): Boolean {
        val v = if (value) 1 else 0
        val oldValue = FU.getAndSet(this, v)
        if (trace !== None) trace { "getAndSet($value):$oldValue" }
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
            field = value
            if (trace !== None) trace { "set($value)" }
        }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { this.value = value }

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public actual fun lazySet(value: Int) {
        FU.lazySet(this, value)
        if (trace !== None) trace { "lazySet($value)" }
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public actual fun compareAndSet(expect: Int, update: Int): Boolean {
        val result = FU.compareAndSet(this, expect, update)
        if (result && trace !== None) trace { "CAS($expect, $update)" }
        return result
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public actual fun getAndSet(value: Int): Int {
        val oldValue = FU.getAndSet(this, value)
        if (trace !== None) trace { "getAndSet($value):$oldValue" }
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndIncrement].
     */
    public actual fun getAndIncrement(): Int {
        val oldValue = FU.getAndIncrement(this)
        if (trace !== None) trace { "getAndInc():$oldValue" }
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndDecrement].
     */
    public actual fun getAndDecrement(): Int {
        val oldValue = FU.getAndDecrement(this)
        if (trace !== None) trace { "getAndDec():$oldValue" }
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndAdd].
     */
    public actual fun getAndAdd(delta: Int): Int {
        val oldValue = FU.getAndAdd(this, delta)
        if (trace !== None) trace { "getAndAdd($delta):$oldValue" }
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.addAndGet].
     */
    public actual fun addAndGet(delta: Int): Int {
        val newValue = FU.addAndGet(this, delta)
        if (trace !== None) trace { "addAndGet($delta):$newValue" }
        return newValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.incrementAndGet].
     */
    public actual fun incrementAndGet(): Int {
        val newValue = FU.incrementAndGet(this)
        if (trace !== None) trace { "incAndGet():$newValue" }
        return newValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.decrementAndGet].
     */
    public actual fun decrementAndGet(): Int {
        val newValue = FU.decrementAndGet(this)
        if (trace !== None) trace { "decAndGet():$newValue" }
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
            field = value
            if (trace !== None) trace { "set($value)" }
        }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { this.value = value }

    /**
     * Maps to [AtomicLongFieldUpdater.lazySet].
     */
    public actual fun lazySet(value: Long) {
        FU.lazySet(this, value)
        if (trace !== None) trace { "lazySet($value)" }
    }

    /**
     * Maps to [AtomicLongFieldUpdater.compareAndSet].
     */
    public actual fun compareAndSet(expect: Long, update: Long): Boolean {
        val result = FU.compareAndSet(this, expect, update)
        if (result && trace !== None) trace { "CAS($expect, $update)" }
        return result
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndSet].
     */
    public actual fun getAndSet(value: Long): Long {
        val oldValue = FU.getAndSet(this, value)
        if (trace !== None) trace { "getAndSet($value):$oldValue" }
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndIncrement].
     */
    public actual fun getAndIncrement(): Long {
        val oldValue = FU.getAndIncrement(this)
        if (trace !== None) trace { "getAndInc():$oldValue" }
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndDecrement].
     */
    public actual fun getAndDecrement(): Long {
        val oldValue = FU.getAndDecrement(this)
        if (trace !== None) trace { "getAndDec():$oldValue" }
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndAdd].
     */
    public actual fun getAndAdd(delta: Long): Long {
        val oldValue = FU.getAndAdd(this, delta)
        if (trace !== None) trace { "getAndAdd($delta):$oldValue" }
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.addAndGet].
     */
    public actual fun addAndGet(delta: Long): Long {
        val newValue = FU.addAndGet(this, delta)
        if (trace !== None) trace { "addAndGet($delta):$newValue" }
        return newValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.incrementAndGet].
     */
    public actual fun incrementAndGet(): Long {
        val newValue = FU.incrementAndGet(this)
        if (trace !== None) trace { "incAndGet():$newValue" }
        return newValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.decrementAndGet].
     */
    public actual fun decrementAndGet(): Long {
        val newValue = FU.decrementAndGet(this)
        if (trace !== None) trace { "decAndGet():$newValue" }
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
