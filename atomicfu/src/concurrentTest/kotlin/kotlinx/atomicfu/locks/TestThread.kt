package kotlinx.atomicfu.locks

internal fun testThread(doConcurrent: () ->  Unit): TestThread = TestThread(doConcurrent)

internal expect class TestThread(toDo: () -> Unit) {
    fun join()
}

expect fun sleepMillis(millis: Long)