package kotlinx.atomicfu.parking

internal fun testThread(doConcurrent: () ->  Unit): TestThread = TestThread { doConcurrent() }

internal expect class TestThread(toDo: () -> Unit) {
    fun join()
}

expect fun sleepMills(millis: Long)