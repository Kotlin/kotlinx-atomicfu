package kotlinx.atomicfu.parking

internal inline fun callAndVerifyNative(vararg expectedReturn: Int, block: () -> Int): Int = block().also {
    check(expectedReturn.contains(it)) {
        "Calling native, expected one return status of ${expectedReturn.joinToString(", ")}, but was $it"
    }
}
