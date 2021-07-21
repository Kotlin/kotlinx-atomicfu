package bytecode_test

import kotlinx.atomicfu.*
import kotlin.coroutines.intrinsics.*

class SplitLvt {
    private val state = atomic(0)
    private val a = 77
    suspend fun foo() {
        val a = suspendBar()
    }
    private inline fun AtomicInt.extensionFun() {
        if (a == 77) throw IllegalStateException("AAAAAAAAAAAA")
        value
    }
    private suspend inline fun suspendBar() {
        state.extensionFun()
        suspendCoroutineUninterceptedOrReturn<Any?> { ucont ->
            COROUTINE_SUSPENDED
        }
    }
}