import kotlinx.atomicfu.*

class IntArithmetic {
    val _x = atomic(0)
    val x get() = _x.value
}

fun doWork(a: IntArithmetic) {
    a._x.getAndSet(3)
    a._x.compareAndSet(3, 8)
}

