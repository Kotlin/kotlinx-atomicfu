package kotlinx.atomicfu.parking

internal inline fun callAndVerifyNative(vararg expectedReturn: Int, block: () -> Int): Int {
    val returnStatus = block()
    if (expectedReturn.all { it != returnStatus }) throw IllegalStateException("Calling native expected return status $expectedReturn, but was $returnStatus")
    return returnStatus
}