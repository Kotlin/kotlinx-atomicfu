package kotlinx.atomicfu.parking

internal expect object PosixParkingDelegator : ParkingDelegator {
    override fun createRef(): Any
    override fun destroyRef(ref: Any)
    override fun timedWait(ref: Any, nanos: Long)
    override fun wait(ref: Any)
    override fun wake(ref: Any)
}

internal inline fun callAndVerifyNative(expectedReturn: Int, block: () -> Int): Int {
    val returnStatus = block()
    if (returnStatus != expectedReturn) throw IllegalStateException("Calling native expected return status $expectedReturn, but was $returnStatus")
    return returnStatus
}