package kotlinx.atomicfu

import kotlin.js.JsName

/**
 * Creates Trace object for tracing atomic operations.
 *
 * Usage: create a separate field for trace and pass to atomic factory function:
 *
 * ```
 * val trace = trace(size)
 * val a = atomic(initialValue, trace)
 * ```
 */
@JsName("atomicfu\$trace\$")
fun trace(size: Int = 32, format: (AtomicInt, String) -> String = { index, text -> "$index: $text" }): Trace = TraceImpl(size, format)

val NO_TRACE = Trace()

/**
 * Default no-op Trace implementation that can be overridden
 */
public open class Trace {

    @JsName("atomicfu\$trace\$append\$")
    @PublishedApi
    internal open fun append(text: String) {}

    inline operator fun invoke(text: () -> String) {
        append(text())
    }
}

class TraceImpl(size: Int, val format: (AtomicInt, String) -> String) : Trace() {
    private val s = { size: Int -> var b = 1; while (b < size) b = b shl 1;b } (size)
    private val mask = s - 1
    private val a = arrayOfNulls<String>(s)
    private val index = atomic(0)

    override fun append(text: String) {
        val i = index.getAndIncrement()
        a[i and mask] = format(index, text)
    }
}

