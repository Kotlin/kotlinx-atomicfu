/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu

import java.util.concurrent.locks.ReentrantLock

internal var interceptor: AtomicOperationInterceptor = DefaultInterceptor
    private set
private val interceptorLock = ReentrantLock()

internal fun lockAndSetInterceptor(impl: AtomicOperationInterceptor) {
    if (!interceptorLock.tryLock() || interceptor !== DefaultInterceptor) {
        error("Interceptor is locked by another test: $interceptor")
    }
    interceptor = impl
}

internal fun unlockAndResetInterceptor(impl: AtomicOperationInterceptor) {
    check(interceptor === impl) { "Unexpected interceptor found: $interceptor" }
    interceptor = DefaultInterceptor
    interceptorLock.unlock()
}

/**
 * Interceptor for modifications of atomic variables.
 */
internal open class AtomicOperationInterceptor {
    open fun <T> beforeUpdate(ref: AtomicRef<T>) {}
    open fun beforeUpdate(ref: AtomicInt) {}
    open fun beforeUpdate(ref: AtomicLong) {}
    open fun beforeUpdate(ref: AtomicBoolean){}
    open fun <T> afterSet(ref: AtomicRef<T>, newValue: T) {}
    open fun afterSet(ref: AtomicInt, newValue: Int) {}
    open fun afterSet(ref: AtomicLong, newValue: Long) {}
    open fun afterSet(ref: AtomicBoolean, newValue: Boolean) {}
    open fun <T> afterRMW(ref: AtomicRef<T>, oldValue: T, newValue: T) {}
    open fun afterRMW(ref: AtomicInt, oldValue: Int, newValue: Int) {}
    open fun afterRMW(ref: AtomicLong, oldValue: Long, newValue: Long) {}
    open fun afterRMW(ref: AtomicBoolean, oldValue: Boolean, newValue: Boolean) {}
}

private object DefaultInterceptor : AtomicOperationInterceptor() {
    override fun toString(): String = "DefaultInterceptor"
}
