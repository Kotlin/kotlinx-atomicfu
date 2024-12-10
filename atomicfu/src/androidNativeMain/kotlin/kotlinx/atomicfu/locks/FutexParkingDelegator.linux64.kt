package kotlinx.atomicfu.locks

internal actual object FutexParkingDelegator: ParkingDelegator {
    actual override fun createFutexPtr(): Long = PosixParkingDelegator.createFutexPtr()
    actual override fun wait(futexPrt: Long): Boolean = PosixParkingDelegator.wait(futexPrt)
    actual override fun wake(futexPrt: Long): Int = PosixParkingDelegator.wake(futexPrt)
    actual override fun manualDeallocate(futexPrt: Long) = PosixParkingDelegator.manualDeallocate(futexPrt)
}
