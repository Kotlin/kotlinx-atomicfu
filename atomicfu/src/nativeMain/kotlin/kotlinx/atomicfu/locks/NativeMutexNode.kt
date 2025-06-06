/*
 * Copyright 2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package kotlinx.atomicfu.locks


public expect class NativeMutexNode() {

    internal var next: NativeMutexNode?

    public fun lock()

    public fun unlock()

    // The lockOwner is used for qos donation on iOS
    internal fun wait(lockOwner: Long)

    internal fun notify()

    internal fun dispose()
}
