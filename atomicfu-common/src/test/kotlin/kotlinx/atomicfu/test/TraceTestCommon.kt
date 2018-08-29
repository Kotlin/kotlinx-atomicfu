package kotlinx.atomicfu.test

import internal_test2.Updater
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.trace
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterWithDefaultTrace {
    private val a = atomic(0)

    private val defaultTrace = trace()
    private val a1 = atomic(5, defaultTrace)

    fun inc(): Int {
        val x = a.incrementAndGet()
        return x
    }

    fun multTen(): Boolean {
        val oldValue = a1.value
        defaultTrace { "current value = $oldValue" }
        return a1.compareAndSet(oldValue, oldValue * 10)
    }

    internal fun getA() = a.value
    internal fun getA1() = a1.value
}

class CounterWithCustomSizeTrace {
    private val t = trace(30)
    private val a = atomic(0, t)

    fun dec(): Int {
        t { "current value = ${a.value}" }
        return a.getAndDecrement()
    }
    internal fun get() = a.value
}

class CounterWithInternalTrace {
    private val u = Updater()
    private val a = atomic(0, u.internalTrace)

    fun update() {
        val oldValue = a.value
        u.internalTrace { "old value = $oldValue" }
        a.compareAndSet(oldValue, oldValue + 5)
    }
    internal fun get() = a.value
}

class InternalTraceTest {
    @Test
    fun internalTraceTest() {
        val cit = CounterWithInternalTrace()
        repeat(5) { cit.update() }
        assertEquals(cit.get(), 25)
    }
}

class MyCounter {
    @Test
    fun testMore() {
        val n = 1000
        val c = CounterWithCustomSizeTrace()
        repeat(n) {
            c.dec()
        }
        assertEquals(-n, c.get())
        println(c.get())
    }

    @Test
    fun testEasy() {
        val c = CounterWithDefaultTrace()
        assertEquals(0, c.getA())
        c.inc()
        assertEquals(1, c.getA())
        println(c.getA())
        c.multTen()
        assertEquals(c.getA1(), 50)
    }
}
