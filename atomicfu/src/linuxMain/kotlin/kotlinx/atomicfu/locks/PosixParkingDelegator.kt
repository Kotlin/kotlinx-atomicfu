package kotlinx.atomicfu.locks

import kotlinx.cinterop.CPointer
import platform.posix.*

internal actual val posixGetTimeClockId: Int
    get() = CLOCK_MONOTONIC

// Sets monotonic clock to prevent time updates from interfering with waiting durations.
internal actual fun pthreadCondAttrSetClock(attr: CPointer<pthread_condattr_t>): Int =
    pthread_condattr_setclock(attr, CLOCK_MONOTONIC)

