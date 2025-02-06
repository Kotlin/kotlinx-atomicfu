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
                            
                            // If other thread is still unparking, and another thread pre unparked. state -> unparked 
                            is UnparkedWhileUnparking -> if (state.compareAndSet(changedState, Unparked)) return

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
                
                // All states below should only be reachable if parking thread has not yet returned.
                is Parked -> throw IllegalStateException("Thread should not be able to call park when it is already parked")
                is Unparking -> throw IllegalStateException("Thread should not be able to call park when it is already parked")
                is UnparkedWhileUnparking -> throw IllegalStateException("Thread should not be able to call park when it is already parked")
            }
        }
    }

    fun unpark() {
        val myUnparkingState = Unparking()
        while (true) {
            when (val currentState = state.value) { 
                
                // Is already unparked
                Unparked -> return
                is UnparkedWhileUnparking -> return
                
                // Other thread is unparking
                is Unparking -> if (state.compareAndSet(currentState, UnparkedWhileUnparking(currentState))) {
                    return
                }
                
                Free -> if (state.compareAndSet(Free, Unparked)) return
                
                // Is parked -> try unpark
                is Parked -> if (state.compareAndSet(currentState, myUnparkingState)) {
                    delegator.wake(currentState.data)
                    
                    while (true) {
                        when (val changedState = state.value) {
                            // state hasn't changed so parker is not awake yet, and responsible for cleanup.
                            myUnparkingState -> if (state.compareAndSet(myUnparkingState, Free)) return
                            
                            // Unparked in the meantime (or parker already set state to free and than unpark)
                            is UnparkedWhileUnparking -> {
                                
                                // Parker still asleep. It's parkers responsibility to cleanup.
                                if (changedState.attempt == myUnparkingState && state.compareAndSet(changedState, Unparked)) return
                                
                                // This means that parker was already gone and parker and other thread is unparking.
                                // Therefore, we are still responsible for cleaning up parking data of our attempt.
                                if (changedState.attempt != myUnparkingState) {
                                    delegator.destroyRef(currentState.data)
                                    return
                                }
                            }
                            
                            // Parker is already gone. Cleanup
                            Free -> {
                                delegator.destroyRef(currentState.data)
                                return
                            }
                            
                            // Parker is already gone and concurrent unpark call. Cleanup.
                            Unparked -> {
                                delegator.destroyRef(currentState.data)
                                return
                            }
                            
                            // Parker is already gone and parked again in the meantime. Cleanup data of first park.
                            is Parked -> {
                                delegator.destroyRef(currentState.data)
                                return
                            }
                        }
                    }
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

// An unpark call happened while unparking was already in process.
private class UnparkedWhileUnparking(val attempt: Unparking) : ParkingState

