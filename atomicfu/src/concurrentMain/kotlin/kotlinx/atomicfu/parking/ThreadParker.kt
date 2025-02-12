package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic
import kotlin.time.DurationUnit
import kotlin.time.TimeSource.Monotonic

/**
 * Multiplatform thread parker.
 */
internal class ThreadParker {
    private val delegator = ParkingDelegator
    private val state = atomic<ParkingState>(Free)

    fun park() = parkWith({ false }) { data ->
        delegator.wait(data) { state.value is Parked }
    }
    
    fun parkNanos(nanos: Long) {
        val mark = Monotonic.markNow()
        parkWith({ mark.elapsedNow().toLong(DurationUnit.NANOSECONDS) >= nanos }) { data ->
            val remainingTime = nanos - mark.elapsedNow().toLong(DurationUnit.NANOSECONDS)
            if (remainingTime > 0) delegator.timedWait(data, remainingTime) { state.value is Parked }
        }
    }

    private fun parkWith(timedOut: () -> Boolean, invokeWait: (ParkingData) -> Unit) {
        while (true) {
            when (state.value) {
                Free -> {
                    val pd = delegator.createRef()
                    // If state changed, cleanup and reiterate.
                    if (!state.compareAndSet(Free, Parked(pd))) {
                        delegator.destroyRef(pd)
                        continue
                    }

                    while (true) {
                        when (val changedState = state.value) {
                            // If still parked invoke wait.
                            is Parked -> {
                                invokeWait(changedState.data)
                                
                                // If timedOut and was able to set to free. Cleanup
                                if (timedOut() && state.compareAndSet(changedState, Free)) {
                                    delegator.destroyRef(pd)
                                    return
                                }
                            }
                            
                            // If other thread is unparking return. Let unparking thread deal with cleanup.
                            is Unparking -> if (state.compareAndSet(changedState, Free)) return

                            // Unparking thread is done unparking
                            Free -> {
                                delegator.destroyRef(pd)
                                return
                            }
                            // Unparking thread is done unparking (And a concurrent thread pre unparked)
                            Unparked -> {
                                delegator.destroyRef(pd)
                                return
                            }
                        }
                    }
                }
                // Parker was pre unparked. Set to free and continue.
                Unparked -> if (state.compareAndSet(Unparked, Free)) return
                
                // The states below should only be reachable if parking thread has not yet returned.
                is Parked -> throw IllegalStateException("Thread should not be able to call park when it is already parked")
                is Unparking -> throw IllegalStateException("Thread should not be able to call park when it is already parked")
            }
        }
    }

    fun unpark() {
        val myUnparkingState = Unparking()
        while (true) {
            when (val currentState = state.value) { 
                
                // Is already unparked
                Unparked -> return
                is Unparking -> return
                
                Free -> if (state.compareAndSet(Free, Unparked)) return
                
                // Is parked -> try unpark
                is Parked -> if (state.compareAndSet(currentState, myUnparkingState)) {
                    delegator.wake(currentState.data)
                    // state hasn't changed so parker is not awake yet, and responsible for cleanup.
                    if (state.compareAndSet(myUnparkingState, Free)) return
                    delegator.destroyRef(currentState.data)
                    return
                }
            }
        }
    }
}
private interface ParkingState
// The Parker is pre-unparked. The next park call will change state to Free and return immediately.
private object Unparked : ParkingState

// Starting state. Can be pre unparked or parked from here. A park call will result in Parked state and suspended thread.
private object Free : ParkingState

// Parker is suspended and can be signaled with data.
private class Parked(val data: ParkingData) : ParkingState

// An unpark call happened while the parker was parked. In process of unparking.
private class Unparking : ParkingState