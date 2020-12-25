package kotlinx.atomicfu

/**
 * All atomicfu declarations are annotated with [@JsName][kotlin.js.JsName] to have specific names in JS output.
 * JS output transformer relies on these mangled names to erase all atomicfu references.
 */

// Atomic factory functions
internal const val ATOMIC_REF_FACTORY = "atomic\$ref\$"
internal const val ATOMIC_REF_FACTORY_BINARY_COMPATIBILITY = "atomic\$ref\$1"
internal const val ATOMIC_INT_FACTORY = "atomic\$int\$"
internal const val ATOMIC_INT_FACTORY_BINARY_COMPATIBILITY = "atomic\$int\$1"
internal const val ATOMIC_LONG_FACTORY = "atomic\$long\$"
internal const val ATOMIC_LONG_FACTORY_BINARY_COMPATIBILITY = "atomic\$long\$1"
internal const val ATOMIC_BOOLEAN_FACTORY = "atomic\$boolean\$"
internal const val ATOMIC_BOOLEAN_FACTORY_BINARY_COMPATIBILITY = "atomic\$boolean\$1"

// Atomic value
internal const val ATOMIC_VALUE = "kotlinx\$atomicfu\$value"

// Atomic operations
internal const val COMPARE_AND_SET = "compareAndSet\$atomicfu\$"
internal const val GET_AND_SET = "getAndSet\$atomicfu\$"
internal const val GET_AND_INCREMENT = "getAndIncrement\$atomicfu\$"
internal const val GET_AND_INCREMENT_LONG = "getAndIncrement\$atomicfu\$long"
internal const val GET_AND_DECREMENT = "getAndDecrement\$atomicfu\$"
internal const val GET_AND_DECREMENT_LONG = "getAndDecrement\$atomicfu\$long"
internal const val INCREMENT_AND_GET = "incrementAndGet\$atomicfu\$"
internal const val INCREMENT_AND_GET_LONG = "incrementAndGet\$atomicfu\$long"
internal const val DECREMENT_AND_GET = "decrementAndGet\$atomicfu\$"
internal const val DECREMENT_AND_GET_LONG = "decrementAndGet\$atomicfu\$long"
internal const val GET_AND_ADD = "getAndAdd\$atomicfu\$"
internal const val GET_AND_ADD_LONG = "getAndAdd\$atomicfu\$long"
internal const val ADD_AND_GET = "addAndGet\$atomicfu\$"
internal const val ADD_AND_GET_LONG = "addAndGet\$atomicfu\$long"

// Atomic arrays constructors
internal const val ATOMIC_ARRAY_OF_NULLS = "AtomicRefArray\$ofNulls"
internal const val ATOMIC_INT_ARRAY = "AtomicIntArray\$int"
internal const val ATOMIC_LONG_ARRAY = "AtomicLongArray\$long"
internal const val ATOMIC_BOOLEAN_ARRAY = "AtomicBooleanArray\$boolean"
internal const val ATOMIC_REF_ARRAY = "AtomicRefArray\$ref"

// Atomic array operations
internal const val ARRAY_SIZE = "size\$atomicfu\$"
internal const val ARRAY_ELEMENT_GET = "get\$atomicfu\$"

// Locks
internal const val REENTRANT_LOCK = "reentrantLock\$atomicfu\$"

// Trace
internal const val TRACE_FACTORY_FUNCTION = "Trace\$atomicfu\$"
internal const val TRACE_BASE_CONSTRUCTOR = "TraceBase\$atomicfu\$"
internal const val TRACE_NAMED = "Trace\$named\$atomicfu\$"
internal const val TRACE_FORMAT_CLASS = "TraceFormat\$atomicfu\$"
internal const val TRACE_FORMAT_FORMAT_FUNCTION = "TraceFormat\$format\$atomicfu\$"

// Trace methods that append logging events to the trace
// [1234] used as a prefix is the number of arguments in the append overload
internal const val TRACE_APPEND_1 = "Trace\$append\$1\$atomicfu\$"
internal const val TRACE_APPEND_2 = "Trace\$append\$2\$atomicfu\$"
internal const val TRACE_APPEND_3 = "Trace\$append\$3\$atomicfu\$"
internal const val TRACE_APPEND_4 = "Trace\$append\$4\$atomicfu\$"