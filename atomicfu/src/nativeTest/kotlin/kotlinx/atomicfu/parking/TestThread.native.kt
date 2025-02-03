package kotlinx.atomicfu.parking

import platform.posix.usleep
import kotlin.native.concurrent.Future
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

internal actual class TestThread actual constructor(private val toDo: () -> Unit) {
    private val future: Future<Unit> = Worker.start().execute(TransferMode.UNSAFE, { toDo }) { toDo -> toDo() }
    actual fun join() = future!!.result
}

actual fun sleepMills(millis: Long) {
    usleep(millis.toUInt() * 1000u)
}