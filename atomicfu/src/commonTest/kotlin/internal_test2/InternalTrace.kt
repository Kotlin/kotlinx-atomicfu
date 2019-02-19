package internal_test2

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.trace

class Updater {
    internal val internalTrace = trace { i, text -> "Updater: $i [$text]" }
    private val t = trace(20)

    val a1 = atomic(5, internalTrace)
    private val a2 = atomic(6, t)
}