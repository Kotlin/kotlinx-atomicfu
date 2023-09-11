import kotlinx.atomicfu.*

class IntArithmetic {
    private val _x = atomic(0)
    val x get() = _x.value

    fun doWork(finalValue: Int) {
        check(x == 0)
        _x.getAndSet(3)
        check(x == 3)
        _x.compareAndSet(3, finalValue)
        check(x == finalValue)
    }
}