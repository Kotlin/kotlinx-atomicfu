/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier", "CanBePrimaryConstructorProperty")

package kotlinx.atomicfu

@JsName("atomic\$ref\$")
public actual fun <T> atomic(initial: T): AtomicRef<T> = AtomicRef<T>(initial)

@JsName("atomic\$int\$")
public actual fun atomic(initial: Int): AtomicInt = AtomicInt(initial)

@JsName("atomic\$long\$")
public actual fun atomic(initial: Long): AtomicLong = AtomicLong(initial)

@JsName("atomic\$boolean\$")
public actual fun atomic(initial: Boolean): AtomicBoolean = AtomicBoolean(initial)

// ==================================== AtomicRef ====================================

public actual class AtomicRef<T> internal constructor(value: T) {
    @JsName("kotlinx\$atomicfu\$value")
    public actual var value: T = value

    public actual inline fun lazySet(value: T) { this.value = value }

    @JsName("compareAndSet\$atomicfu")
    public actual fun compareAndSet(expect: T, update: T): Boolean {
        if (value !== expect) return false
        value = update
        return true
    }

    @JsName("getAndSet\$atomicfu")
    public actual fun getAndSet(value: T): T {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicBoolean ====================================

public actual class AtomicBoolean internal constructor(value: Boolean) {
    @JsName("kotlinx\$atomicfu\$value")
    public actual var value: Boolean = value

    public actual inline fun lazySet(value: Boolean) {
        this.value = value
    }

    @JsName("compareAndSet\$atomicfu")
    public actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    @JsName("getAndSet\$atomicfu")
    public actual fun getAndSet(value: Boolean): Boolean {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicInt ====================================

public actual class AtomicInt internal constructor(value: Int) {
    @JsName("kotlinx\$atomicfu\$value")
    public actual var value: Int = value

    public actual inline fun lazySet(value: Int) { this.value = value }

    @JsName("compareAndSet\$atomicfu")
    public actual fun compareAndSet(expect: Int, update: Int): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    @JsName("getAndSet\$atomicfu")
    public actual fun getAndSet(value: Int): Int {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    @JsName("getAndIncrement\$atomicfu")
    public actual fun getAndIncrement(): Int = value++

    @JsName("getAndDecrement\$atomicfu")
    public actual fun getAndDecrement(): Int = value--

    @JsName("getAndAdd\$atomicfu")
    public actual fun getAndAdd(delta: Int): Int {
        val oldValue = value
        value += delta
        return oldValue
    }

    @JsName("addAndGet\$atomicfu")
    public actual fun addAndGet(delta: Int): Int {
        value += delta
        return value
    }

    @JsName("incrementAndGet\$atomicfu")
    public actual fun incrementAndGet(): Int = ++value

    @JsName("decrementAndGet\$atomicfu")
    public actual fun decrementAndGet(): Int = --value

    public actual inline operator fun plusAssign(delta: Int) { getAndAdd(delta) }

    public actual inline operator fun minusAssign(delta: Int) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()
}

// ==================================== AtomicLong ====================================

public actual class AtomicLong internal constructor(value: Long) {
    @JsName("kotlinx\$atomicfu\$value")
    public actual var value: Long = value

    public actual inline fun lazySet(value: Long) { this.value = value }

    @JsName("compareAndSet\$atomicfu")
    public actual fun compareAndSet(expect: Long, update: Long): Boolean {
        if (value != expect) return false
        value = update
        return true
    }

    @JsName("getAndSet\$atomicfu")
    public actual fun getAndSet(value: Long): Long {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    @JsName("getAndIncrement\$atomicfu\$long")
    public actual fun getAndIncrement(): Long = value++

    @JsName("getAndDecrement\$atomicfu\$long")
    public actual fun getAndDecrement(): Long = value--

    @JsName("getAndAdd\$atomicfu\$long")
    public actual fun getAndAdd(delta: Long): Long {
        val oldValue = value
        value += delta
        return oldValue
    }

    @JsName("addAndGet\$atomicfu\$long")
    public actual fun addAndGet(delta: Long): Long {
        value += delta
        return value
    }

    @JsName("incrementAndGet\$atomicfu\$long")
    public actual fun incrementAndGet(): Long = ++value

    @JsName("decrementAndGet\$atomicfu\$long")
    public actual fun decrementAndGet(): Long = --value

    public actual inline operator fun plusAssign(delta: Long) { getAndAdd(delta) }

    public actual inline operator fun minusAssign(delta: Long) { getAndAdd(-delta) }

    override fun toString(): String = value.toString()
}
