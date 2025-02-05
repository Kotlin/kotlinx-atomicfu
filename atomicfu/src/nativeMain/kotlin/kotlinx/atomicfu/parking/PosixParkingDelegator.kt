package kotlinx.atomicfu.parking

internal inline fun callAndVerifyNative(vararg expectedReturn: Int, block: () -> Int): Int {
    val returnStatus = block()
    if (expectedReturn.all { it != returnStatus }) 
        throw IllegalStateException("Calling native, expected one return status of ${expectedReturn.joinToString(", ")}, but was $returnStatus")
    return returnStatus
}