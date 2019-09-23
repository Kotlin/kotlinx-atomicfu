package kotlinx.atomicfu

public actual typealias SynchronizedObject = Any

public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T =
    block()