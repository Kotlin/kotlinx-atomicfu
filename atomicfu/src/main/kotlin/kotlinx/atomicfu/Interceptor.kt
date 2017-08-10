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
    open fun <T> onSet(ref: AtomicRef<T>, newValue: T) {}
    open fun <T> onRMW(ref: AtomicRef<T>, oldValue: T, newValue: T) { onSet(ref, newValue) }
    open fun onSet(ref: AtomicInt, newValue: Int) {}
    open fun onRMW(ref: AtomicInt, oldValue: Int, newValue: Int) { onSet(ref, newValue) }
    open fun onSet(ref: AtomicLong, newValue: Long) {}
    open fun onRMW(ref: AtomicLong, oldValue: Long, newValue: Long) { onSet(ref, newValue) }
}

private object DefaultInterceptor : AtomicOperationInterceptor() {
    override fun toString(): String = "DefaultInterceptor"
}
