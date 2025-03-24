package kotlinx.atomicfu.locks

internal inline fun callAndVerifyNative(vararg expectedReturn: Int, getErrno: () -> Int, block: () -> Int): Int = block().also {
    check(expectedReturn.contains(it)) {
        "Calling native, expected one return status of ${expectedReturn.joinToString(", ")}, but was $it. With errno: ${getErrno()}"
    }
}
