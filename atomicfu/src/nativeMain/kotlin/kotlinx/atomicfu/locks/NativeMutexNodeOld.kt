package kotlinx.atomicfu.locks

public expect class NativeMutexNode() {
    internal var next: NativeMutexNode?

    public fun lock()

    public fun unlock()
}