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
                            
                            // If other thread is unparking return. Let other thread deal with cleanup.
                            is Unparking -> if (state.compareAndSet(changedState, Free)) return
                            
                            // Other thread is done unparking, set to free and cleanup.
                            Unparked -> if (state.compareAndSet(Unparked, Free)) {
                                delegator.destroyRef(pd)
                                return
                            }
                            Free -> throw IllegalStateException("Only parker thread can set to free")
                        }
                    }
                }
                // Parker was pre unparked. Set to free and continue.
                Unparked -> if (state.compareAndSet(Unparked, Free)) return
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
                
                // Other thread is unparking
                is Unparking -> return
                
                // Is free so pre unpark
                Free -> if (state.compareAndSet(Free, Unparked)) return
                
                // Is parked -> try unpark
                is Parked -> if (state.compareAndSet(currentState, myUnparkingState)) {
                    delegator.wake(currentState.data)
                    
                    // If state changed (to free) while unparking destroy reference. Otherwise parker thread is responsible.
                    if (!state.compareAndSet(myUnparkingState, Unparked)) delegator.destroyRef(currentState.data)
                    return
                }
            }
        }
    }
}
private interface ParkingState
private object Unparked : ParkingState
private object Free : ParkingState
private class Parked(val data: ParkingData) : ParkingState
private class Unparking : ParkingState

