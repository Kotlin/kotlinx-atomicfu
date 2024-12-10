package kotlinx.atomicfu.locks

/**
 * Experimental! Might be unwise to use for apple targets.
 * 
 */
internal expect object FutexParkingDelegator: ParkingDelegator {
    override fun createFutexPtr(): Long
    override fun wait(futexPrt: Long): Boolean
    override fun wake(futexPrt: Long): Int
    override fun manualDeallocate(futexPrt: Long)
}
