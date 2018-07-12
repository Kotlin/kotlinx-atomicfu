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

package kotlinx.atomicfu

/**
 * Creates atomic reference with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic<Type>(initial)
 * ```
 */
public expect fun <T> atomic(initial: T): AtomicRef<T>

/**
 * Creates atomic [Int] with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic(initialInt)
 * ```
 */
public expect fun atomic(initial: Int): AtomicInt

/**
 * Creates atomic [Long] with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic(initialLong)
 * ```
 */
public expect fun atomic(initial: Long): AtomicLong

/**
 * Creates atomic [Boolean] with a given [initial] value.
 *
 * It can only be used in initialize of private read-only property, like this:
 *
 * ```
 * private val f = atomic(initialBoolean)
 * ```
 */
public expect fun atomic(initial: Boolean): AtomicBoolean

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