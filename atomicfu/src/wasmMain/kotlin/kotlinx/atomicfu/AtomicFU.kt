/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier", "CanBePrimaryConstructorProperty")

package kotlinx.atomicfu

import kotlin.reflect.KProperty
import kotlinx.atomicfu.TraceBase.None

public actual fun <T> atomic(initial: T, trace: TraceBase): AtomicRef<T> = AtomicRef<T>(initial)

public actual fun <T> atomic(initial: T): AtomicRef<T> = atomic(initial, None)

public actual fun atomic(initial: Int, trace: TraceBase): AtomicInt = AtomicInt(initial)

public actual fun atomic(initial: Int): AtomicInt = atomic(initial, None)

public actual fun atomic(initial: Long, trace: TraceBase): AtomicLong = AtomicLong(initial)

public actual fun atomic(initial: Long): AtomicLong = atomic(initial, None)

public actual fun atomic(initial: Boolean, trace: TraceBase): AtomicBoolean = AtomicBoolean(initial)

public actual fun atomic(initial: Boolean): AtomicBoolean = atomic(initial, None)

// ==================================== AtomicRef ====================================

public actual class AtomicRef<T> internal constructor(value: T) {
    public actual var value: T = value

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { this.value = value }

    public actual inline fun lazySet(value: T) { this.value = value }

    public actual fun compareAndSet(expect: T, update: T): Boolean {
        if (value !== expect) return false
        value = update
        return true
    }

    public actual fun getAndSet(value: T): T {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicBoolean ====================================

public actual class AtomicBoolean internal constructor(value: Boolean) {
    public actual var value: Boolean = value

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { this.value = value }

    public actual inline fun lazySet(value: Boolean) {
        this.value = value
    }

    public actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    public actual fun getAndSet(value: Boolean): Boolean {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicInt ====================================

public actual class AtomicInt internal constructor(value: Int) {
    public actual var value: Int = value

    actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { this.value = value }

    public actual inline fun lazySet(value: Int) { this.value = value }

    public actual fun compareAndSet(expect: Int, update: Int): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    public actual fun getAndSet(value: Int): Int {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    public actual fun getAndIncrement(): Int = value++

    public actual fun getAndDecrement(): Int = value--

    public actual fun getAndAdd(delta: Int): Int {
        val oldValue = value
        value += delta
        return oldValue
    }

    public actual fun addAndGet(delta: Int): Int {
        value += delta
        return value
    }

    public actual fun incrementAndGet(): Int = ++value

    public actual fun decrementAndGet(): Int = --value

    public actual inline operator fun plusAssign(delta: Int) { getAndAdd(delta) }

    public actual inline operator fun minusAssign(delta: Int) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicLong ====================================

public actual class AtomicLong internal constructor(value: Long) {
    public actual var value: Long = value

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { this.value = value }

    public actual inline fun lazySet(value: Long) { this.value = value }

    public actual fun compareAndSet(expect: Long, update: Long): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    public actual fun getAndSet(value: Long): Long {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    public actual fun getAndIncrement(): Long = value++

    public actual fun getAndDecrement(): Long = value--

    public actual fun getAndAdd(delta: Long): Long {
        val oldValue = value
        value += delta
        return oldValue
    }

    public actual fun addAndGet(delta: Long): Long {
        value += delta
        return value
    }

    public actual fun incrementAndGet(): Long = ++value

    public actual fun decrementAndGet(): Long = --value

    public actual inline operator fun plusAssign(delta: Long) { getAndAdd(delta) }

    public actual inline operator fun minusAssign(delta: Long) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()
}