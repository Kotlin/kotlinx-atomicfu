package kotlinx.atomicfu.locks

import kotlinx.cinterop.CPointer
import platform.posix.CLOCK_REALTIME
import platform.posix.*

// CLOCK_REALTIME should be equal to CLOCK_SYSTEM on darwin. 
// https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/osfmk/mach/clock_types.h#L70-L73
// Where CLOCK_CALENDAR is the time from epoch.
internal actual val posixGetTimeClockId: Int
    get() = CLOCK_REALTIME

actual fun pthreadCondAttrSetClock(attr: CPointer<pthread_condattr_t>): Int = 0
