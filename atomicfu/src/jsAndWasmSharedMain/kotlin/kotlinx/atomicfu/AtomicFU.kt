/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress(
    "NOTHING_TO_INLINE",
    "RedundantVisibilityModifier",
    "CanBePrimaryConstructorProperty",
    "INVISIBLE_REFERENCE",
    "INVISIBLE_MEMBER"
)

package kotlinx.atomicfu

import kotlinx.atomicfu.TraceBase.None
import kotlin.internal.InlineOnly
import kotlin.reflect.KProperty

@OptionalJsName(ATOMIC_REF_FACTORY)
public actual fun <T> atomic(initial: T, trace: TraceBase): AtomicRef<T> = AtomicRef<T>(initial)

@OptionalJsName(ATOMIC_REF_FACTORY_BINARY_COMPATIBILITY)
public actual fun <T> atomic(initial: T): AtomicRef<T> = atomic(initial, None)

@OptionalJsName(ATOMIC_INT_FACTORY)
public actual fun atomic(initial: Int, trace: TraceBase): AtomicInt = AtomicInt(initial)

@OptionalJsName(ATOMIC_INT_FACTORY_BINARY_COMPATIBILITY)
public actual fun atomic(initial: Int): AtomicInt = atomic(initial, None)

@OptionalJsName(ATOMIC_LONG_FACTORY)
public actual fun atomic(initial: Long, trace: TraceBase): AtomicLong = AtomicLong(initial)

@OptionalJsName(ATOMIC_LONG_FACTORY_BINARY_COMPATIBILITY)
public actual fun atomic(initial: Long): AtomicLong = atomic(initial, None)

@OptionalJsName(ATOMIC_BOOLEAN_FACTORY)
public actual fun atomic(initial: Boolean, trace: TraceBase): AtomicBoolean = AtomicBoolean(initial)

@OptionalJsName(ATOMIC_BOOLEAN_FACTORY_BINARY_COMPATIBILITY)
public actual fun atomic(initial: Boolean): AtomicBoolean = atomic(initial, None)

// ==================================== AtomicRef ====================================

public actual class AtomicRef<T> internal constructor(value: T) {
    @OptionalJsName(ATOMIC_VALUE)
    public actual var value: T = value

    @InlineOnly
    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    @InlineOnly
    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { this.value = value }

    public actual inline fun lazySet(value: T) { this.value = value }

    @OptionalJsName(COMPARE_AND_SET)
    public actual fun compareAndSet(expect: T, update: T): Boolean {
        if (value !== expect) return false
        value = update
        return true
    }

    @OptionalJsName(GET_AND_SET)
    public actual fun getAndSet(value: T): T {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicBoolean ====================================

public actual class AtomicBoolean internal constructor(value: Boolean) {
    @OptionalJsName(ATOMIC_VALUE)
    public actual var value: Boolean = value

    @InlineOnly
    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = value

    @InlineOnly
    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { this.value = value }

    public actual inline fun lazySet(value: Boolean) {
        this.value = value
    }

    @OptionalJsName(COMPARE_AND_SET)
    public actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    @OptionalJsName(GET_AND_SET)
    public actual fun getAndSet(value: Boolean): Boolean {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicInt ====================================

public actual class AtomicInt internal constructor(value: Int) {
    @OptionalJsName(ATOMIC_VALUE)
    public actual var value: Int = value

    @InlineOnly
    actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = value

    @InlineOnly
    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { this.value = value }

    public actual inline fun lazySet(value: Int) { this.value = value }

    @OptionalJsName(COMPARE_AND_SET)
    public actual fun compareAndSet(expect: Int, update: Int): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    @OptionalJsName(GET_AND_SET)
    public actual fun getAndSet(value: Int): Int {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    @OptionalJsName(GET_AND_INCREMENT)
    public actual fun getAndIncrement(): Int = value++

    @OptionalJsName(GET_AND_DECREMENT)
    public actual fun getAndDecrement(): Int = value--

    @OptionalJsName(GET_AND_ADD)
    public actual fun getAndAdd(delta: Int): Int {
        val oldValue = value
        value += delta
        return oldValue
    }

    @OptionalJsName(ADD_AND_GET)
    public actual fun addAndGet(delta: Int): Int {
        value += delta
        return value
    }

    @OptionalJsName(INCREMENT_AND_GET)
    public actual fun incrementAndGet(): Int = ++value

    @OptionalJsName(DECREMENT_AND_GET)
    public actual fun decrementAndGet(): Int = --value

    public actual inline operator fun plusAssign(delta: Int) { getAndAdd(delta) }

    public actual inline operator fun minusAssign(delta: Int) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicLong ====================================

public actual class AtomicLong internal constructor(value: Long) {
    @OptionalJsName(ATOMIC_VALUE)
    public actual var value: Long = value

    @InlineOnly
    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    @InlineOnly
    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { this.value = value }

    public actual inline fun lazySet(value: Long) { this.value = value }

    @OptionalJsName(COMPARE_AND_SET)
    public actual fun compareAndSet(expect: Long, update: Long): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    @OptionalJsName(GET_AND_SET)
    public actual fun getAndSet(value: Long): Long {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    @OptionalJsName(GET_AND_INCREMENT_LONG)
    public actual fun getAndIncrement(): Long = value++

    @OptionalJsName(GET_AND_DECREMENT_LONG)
    public actual fun getAndDecrement(): Long = value--

    @OptionalJsName(GET_AND_ADD_LONG)
    public actual fun getAndAdd(delta: Long): Long {
        val oldValue = value
        value += delta
        return oldValue
    }

    @OptionalJsName(ADD_AND_GET_LONG)
    public actual fun addAndGet(delta: Long): Long {
        value += delta
        return value
    }

    @OptionalJsName(INCREMENT_AND_GET_LONG)
    public actual fun incrementAndGet(): Long = ++value

    @OptionalJsName(DECREMENT_AND_GET_LONG)
    public actual fun decrementAndGet(): Long = --value

    public actual inline operator fun plusAssign(delta: Long) { getAndAdd(delta) }

    public actual inline operator fun minusAssign(delta: Long) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()
}