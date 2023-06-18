/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier", "CanBePrimaryConstructorProperty")

package kotlinx.atomicfu

import kotlin.concurrent.AtomicInt as KAtomicInt
import kotlin.concurrent.AtomicLong as KAtomicLong
import kotlin.concurrent.AtomicReference as KAtomicRef
import kotlin.native.concurrent.isFrozen
import kotlin.native.concurrent.freeze
import kotlin.reflect.KProperty
import kotlinx.atomicfu.TraceBase.None

public actual fun <T> atomic(initial: T, trace: TraceBase): AtomicRef<T> = AtomicRef<T>(KAtomicRef(initial))
public actual fun <T> atomic(initial: T): AtomicRef<T> = atomic(initial, None)
public actual fun atomic(initial: Int, trace: TraceBase): AtomicInt = AtomicInt(KAtomicInt(initial))
public actual fun atomic(initial: Int): AtomicInt = atomic(initial, None)
public actual fun atomic(initial: Long, trace: TraceBase): AtomicLong = AtomicLong(KAtomicLong(initial))
public actual fun atomic(initial: Long): AtomicLong = atomic(initial, None)
public actual fun atomic(initial: Boolean, trace: TraceBase): AtomicBoolean = AtomicBoolean(KAtomicInt(if (initial) 1 else 0))
public actual fun atomic(initial: Boolean): AtomicBoolean = atomic(initial, None)

// ==================================== AtomicRef ====================================

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual value class AtomicRef<T> internal constructor(@PublishedApi internal val a: KAtomicRef<T>) {
    public actual inline var value: T
        get() = a.value
        set(value) {
            if (a.isFrozen) value.freeze()
            a.value = value
        }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { this.value = value }

    public actual inline fun lazySet(value: T) {
        if (a.isFrozen) value.freeze()
        a.value = value
    }

    public actual inline fun compareAndSet(expect: T, update: T): Boolean {
        if (a.isFrozen) update.freeze()
        return a.compareAndSet(expect, update)
    }

    public actual fun getAndSet(value: T): T {
        if (a.isFrozen) value.freeze()
        while (true) {
            val cur = a.value
            if (cur === value) return cur
            if (a.compareAndExchange(cur, value) === cur) return cur
        }
    }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicBoolean ====================================

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual value class AtomicBoolean internal constructor(@PublishedApi internal val a: KAtomicInt) {
    public actual inline var value: Boolean
        get() = a.value != 0
        set(value) { a.value = if (value) 1 else 0 }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { this.value = value }

    public actual inline fun lazySet(value: Boolean) { this.value = value }

    public actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        val iExpect = if (expect) 1 else 0
        val iUpdate = if (update) 1 else 0
        return a.compareAndSet(iExpect, iUpdate)
    }

    public actual fun getAndSet(value: Boolean): Boolean {
        val iValue = if (value) 1 else 0
        while (true) {
            val cur = a.value
            if (cur == iValue) return value
            if (a.compareAndSet(cur, iValue)) return cur != 0
        }
    }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicInt ====================================

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual value class AtomicInt internal constructor(@PublishedApi internal val a: KAtomicInt) {
    public actual inline var value: Int
        get() = a.value
        set(value) { a.value = value }

    actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { this.value = value }

    public actual inline fun lazySet(value: Int) { a.value = value }

    public actual inline fun compareAndSet(expect: Int, update: Int): Boolean =
        a.compareAndSet(expect, update)

    public actual fun getAndSet(value: Int): Int {
        while (true) {
            val cur = a.value
            if (cur == value) return cur
            if (a.compareAndSet(cur, value)) return cur
        }
    }

    public actual inline fun getAndIncrement(): Int = a.addAndGet(1) - 1
    public actual inline fun getAndDecrement(): Int = a.addAndGet(-1) + 1
    public actual inline fun getAndAdd(delta: Int): Int = a.addAndGet(delta) - delta
    public actual inline fun addAndGet(delta: Int): Int = a.addAndGet(delta)
    public actual inline fun incrementAndGet(): Int = a.addAndGet(1)
    public actual inline fun decrementAndGet(): Int = a.addAndGet(-1)

    public actual inline operator fun plusAssign(delta: Int) { getAndAdd(delta) }
    public actual inline operator fun minusAssign(delta: Int) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicLong ====================================

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual value class AtomicLong internal constructor(@PublishedApi internal val a: KAtomicLong) {
    public actual inline var value: Long
        get() = a.value
        set(value) { a.value = value }

    public actual inline operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    public actual inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { this.value = value }

    public actual inline fun lazySet(value: Long) { a.value = value }

    public actual inline fun compareAndSet(expect: Long, update: Long): Boolean =
        a.compareAndSet(expect, update)

    public actual fun getAndSet(value: Long): Long {
        while (true) {
            val cur = a.value
            if (cur == value) return cur
            if (a.compareAndSet(cur, value)) return cur
        }
    }

    public actual inline fun getAndIncrement(): Long = a.addAndGet(1L) - 1
    public actual inline fun getAndDecrement(): Long = a.addAndGet(-1L) + 1
    public actual inline fun getAndAdd(delta: Long): Long = a.addAndGet(delta) - delta
    public actual inline fun addAndGet(delta: Long): Long = a.addAndGet(delta)
    public actual inline fun incrementAndGet(): Long = a.addAndGet(1L)
    public actual inline fun decrementAndGet(): Long = a.addAndGet(-1L)

    public actual inline operator fun plusAssign(delta: Long) { getAndAdd(delta) }
    public actual inline operator fun minusAssign(delta: Long) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()
}

