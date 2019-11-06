package kotlinx.atomicfu.locks

public actual typealias SynchronizedObject = Any

@JsName("reentrantLock\$atomicfu")
public val Lock = ReentrantLock()

public actual inline fun reentrantLock() = Lock

public actual class ReentrantLock {
    actual inline fun lock(): Unit {}
    actual inline fun tryLock() = true
    actual inline fun unlock(): Unit {}
}

public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T =
    block()