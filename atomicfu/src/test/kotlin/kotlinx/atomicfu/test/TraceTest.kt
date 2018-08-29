package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.Test
import kotlin.test.assertEquals

class Counter {
    private val t = trace(64) { index, text -> "$index: [${Thread.currentThread().name}] $text" }
    private val a = atomic(0, t)

    fun inc(): Int {
        t { "inc() invoked" }
        val x = a.incrementAndGet()
        t { "inc() = $x" }
        return x
    }

    internal fun get() = a.value
}

class CounterLFTest : LockFreedomTestEnvironment("CounterLFTest") {
    private val c = Counter()

    @Test
    fun testLockFreedom() {
        repeat(10) { id ->
            testThread("Inc-$id") {
                c.inc()
            }
        }
        repeat(2) { id ->
            testThread("Get-$id") {
                c.get()
            }
        }
        performTest(10)
        println(c.get())
    }
}

