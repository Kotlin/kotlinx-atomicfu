/*
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("AtomicFU")
@file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier")

package kotlinx.atomicfu

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

/**
 * Creates atomic reference with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic<Type>(initial)
 * ```
 */
public fun <T> atomic(initial: T): AtomicRef<T> = AtomicRef<T>(initial)

/**
 * Creates atomic [Int] with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic(initialInt)
 * ```
 */
public fun atomic(initial: Int): AtomicInt = AtomicInt(initial)

/**
 * Creates atomic [Long] with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic(initialLong)
 * ```
 */
public fun atomic(initial: Long): AtomicLong = AtomicLong(initial)

// ==================================== AtomicRef ====================================

/**
 * Atomic reference to a variable of type [T] with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
@Suppress("UNCHECKED_CAST")
public class AtomicRef<T> internal constructor(value: T) {
    /**
     * Reading/writing this property maps to read/write of volatile variable.
     */
    @Volatile
    public var value: T = value
        set(value) {
            interceptor.beforeUpdate(this)
            field = value
            interceptor.afterSet(this, value)
        }

    /**
     * Maps to [AtomicReferenceFieldUpdater.lazySet].
     */
    public fun lazySet(value: T) {
        interceptor.beforeUpdate(this)
        FU.lazySet(this, value)
        interceptor.afterSet(this, value)
    }

    /**
     * Maps to [AtomicReferenceFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: T, update: T): Boolean {
        interceptor.beforeUpdate(this)
        val result = FU.compareAndSet(this, expect, update)
        if (result) interceptor.afterRMW(this, expect, update)
        return result
    }

    /**
     * Maps to [AtomicReferenceFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: T): T {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndSet(this, value) as T
        interceptor.afterRMW(this, oldValue, value)
        return oldValue
    }

    override fun toString(): String = value.toString()

    private companion object {
        private val FU = AtomicReferenceFieldUpdater.newUpdater(AtomicRef::class.java, Any::class.java, "value")
    }
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

// ==================================== AtomicInt ====================================

/**
 * Atomic reference to an [Int] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
public class AtomicInt internal constructor(value: Int) {
    /**
     * Reads/writes of this property maps to read/write of volatile variable.
     */
    @Volatile
    public var value: Int = value
        set(value) {
            interceptor.beforeUpdate(this)
            field = value
            interceptor.afterSet(this, value)
        }

    /**
     * Maps to [AtomicIntegerFieldUpdater.lazySet].
     */
    public fun lazySet(value: Int) {
        interceptor.beforeUpdate(this)
        FU.lazySet(this, value)
        interceptor.afterSet(this, value)
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: Int, update: Int): Boolean {
        interceptor.beforeUpdate(this)
        val result = FU.compareAndSet(this, expect, update)
        if (result) interceptor.afterRMW(this, expect, update)
        return result
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: Int): Int {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndSet(this, value)
        interceptor.afterRMW(this, oldValue, value)
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndIncrement].
     */
    public fun getAndIncrement(): Int {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndIncrement(this)
        interceptor.afterRMW(this, oldValue, oldValue + 1)
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndDecrement].
     */
    public fun getAndDecrement(): Int {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndDecrement(this)
        interceptor.afterRMW(this, oldValue, oldValue - 1)
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.getAndAdd].
     */
    public fun getAndAdd(delta: Int): Int {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndAdd(this, delta)
        interceptor.afterRMW(this, oldValue, oldValue + delta)
        return oldValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.addAndGet].
     */
    public fun addAndGet(delta: Int): Int {
        interceptor.beforeUpdate(this)
        val newValue = FU.addAndGet(this, delta)
        interceptor.afterRMW(this, newValue - delta, newValue)
        return newValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.incrementAndGet].
     */
    public fun incrementAndGet(): Int {
        interceptor.beforeUpdate(this)
        val newValue = FU.incrementAndGet(this)
        interceptor.afterRMW(this, newValue - 1, newValue)
        return newValue
    }

    /**
     * Maps to [AtomicIntegerFieldUpdater.decrementAndGet].
     */
    public fun decrementAndGet(): Int {
        interceptor.beforeUpdate(this)
        val newValue = FU.decrementAndGet(this)
        interceptor.afterRMW(this, newValue + 1, newValue)
        return newValue
    }

    /**
     * Performs atomic addition of [delta].
     */
    public inline operator fun plusAssign(delta: Int) { getAndAdd(delta) }

    /**
     * Performs atomic subtraction of [delta].
     */
    public inline operator fun minusAssign(delta: Int) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()

    private companion object {
        private val FU = AtomicIntegerFieldUpdater.newUpdater(AtomicInt::class.java, "value")
    }
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

// ==================================== AtomicLong ====================================

/**
 * Atomic reference to a [Long] variable with volatile reads/writes via
 * [value] property and various atomic read-modify-write operations
 * like [compareAndSet] and others.
 */
public class AtomicLong internal constructor(value: Long) {
    /**
     * Reads/writes of this property maps to read/write of volatile variable.
     */
    @Volatile
    public var value: Long = value
        set(value) {
            interceptor.beforeUpdate(this)
            field = value
            interceptor.afterSet(this, value)
        }

    /**
     * Maps to [AtomicLongFieldUpdater.lazySet].
     */
    public fun lazySet(value: Long) {
        interceptor.beforeUpdate(this)
        FU.lazySet(this, value)
        interceptor.afterSet(this, value)
    }

    /**
     * Maps to [AtomicLongFieldUpdater.compareAndSet].
     */
    public fun compareAndSet(expect: Long, update: Long): Boolean {
        interceptor.beforeUpdate(this)
        val result = FU.compareAndSet(this, expect, update)
        if (result) interceptor.afterRMW(this, expect, update)
        return result
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndSet].
     */
    public fun getAndSet(value: Long): Long {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndSet(this, value)
        interceptor.afterRMW(this, oldValue, value)
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndIncrement].
     */
    public fun getAndIncrement(): Long {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndIncrement(this)
        interceptor.afterRMW(this, oldValue, oldValue + 1)
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndDecrement].
     */
    public fun getAndDecrement(): Long {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndDecrement(this)
        interceptor.afterRMW(this, oldValue, oldValue - 1)
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.getAndAdd].
     */
    public fun getAndAdd(delta: Long): Long {
        interceptor.beforeUpdate(this)
        val oldValue = FU.getAndAdd(this, delta)
        interceptor.afterRMW(this, oldValue, oldValue + delta)
        return oldValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.addAndGet].
     */
    public fun addAndGet(delta: Long): Long {
        interceptor.beforeUpdate(this)
        val newValue = FU.addAndGet(this, delta)
        interceptor.afterRMW(this, newValue - delta, newValue)
        return newValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.incrementAndGet].
     */
    public fun incrementAndGet(): Long {
        interceptor.beforeUpdate(this)
        val newValue = FU.incrementAndGet(this)
        interceptor.afterRMW(this, newValue - 1, newValue)
        return newValue
    }

    /**
     * Maps to [AtomicLongFieldUpdater.decrementAndGet].
     */
    public fun decrementAndGet(): Long {
        interceptor.beforeUpdate(this)
        val newValue = FU.decrementAndGet(this)
        interceptor.afterRMW(this, newValue + 1, newValue)
        return newValue
    }

    /**
     * Performs atomic addition of [delta].
     */
    public inline operator fun plusAssign(delta: Long) { getAndAdd(delta) }

    /**
     * Performs atomic subtraction of [delta].
     */
    public inline operator fun minusAssign(delta: Long) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()

    private companion object {
        private val FU = AtomicLongFieldUpdater.newUpdater(AtomicLong::class.java, "value")
    }
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
