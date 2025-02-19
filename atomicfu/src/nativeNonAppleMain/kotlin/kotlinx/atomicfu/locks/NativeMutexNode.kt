/*
 * Copyright 2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
package kotlinx.atomicfu.locks


public expect class NativeMutexNode() {

    internal var next: NativeMutexNode?

    public fun lock()

    public fun unlock()

    public fun wait()

    public fun notify()

    public fun dispose()
}
