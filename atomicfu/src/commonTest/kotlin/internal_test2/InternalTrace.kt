package internal_test2

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.Trace

class Updater {
    internal val internalTrace = Trace { i, text -> "Updater: $i [$text]" }
    private val t = Trace(20)

    val a1 = atomic(5, internalTrace)
    private val a2 = atomic(6, t)
}