package kotlinx.atomicfu.locks

import platform.posix.errno

internal inline fun callAndVerify(expectedReturn: Int, block: () -> Int) = block().also {
        check(it == expectedReturn) {
            errorString(it, expectedReturn)
        }
    }

internal inline fun callAndVerify(firstExpectedReturn: Int, secondExpectedReturn: Int, block: () -> Int) = block().also {
        check(it == firstExpectedReturn || it == secondExpectedReturn) {
            errorString(it, firstExpectedReturn, secondExpectedReturn)
        }
    }

private fun errorString(actualValue: Int, vararg expectedReturn: Int) =
    "Calling native, expected one return status of ${expectedReturn.joinToString(", ")}, but was $actualValue. With errno: $errno"