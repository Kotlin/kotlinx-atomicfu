package kotlinx.atomicfu.locks

public expect open class SynchronizedObject() // marker abstract class

public expect inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T

