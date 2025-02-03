package kotlinx.atomicfu.parking
import kotlin.concurrent.thread

internal actual class TestThread actual constructor(toDo: () -> Unit) {
    private val th = thread { toDo() } 
    actual fun join() = th.join()
}

actual fun sleepMills(millis: Long) = Thread.sleep(millis)