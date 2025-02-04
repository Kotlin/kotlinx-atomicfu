package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic
import kotlin.time.DurationUnit
import kotlin.time.TimeSource.Monotonic

internal class ThreadParker {
    private val delegator = ParkingDelegator
    private val state = atomic<ParkingState>(Free)

    fun park() = parkWith({ false }) { data ->
        delegator.wait(data)
    }
    fun parkNanos(nanos: Long) {
        val mark = Monotonic.markNow()
        parkWith({ mark.elapsedNow().toLong(DurationUnit.NANOSECONDS) >= nanos }) { data ->
            val remainingTime = nanos - mark.elapsedNow().toLong(DurationUnit.NANOSECONDS)
            if (remainingTime > 0) delegator.timedWait(data, remainingTime)
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
                            is Parked -> {
                                invokeWait(changedState.data)
                                if (timedOut() && state.compareAndSet(changedState, Free)) {
                                    delegator.destroyRef(pd)
                                    return
                                }
                            }
                            Unparking -> if (state.compareAndSet(Unparking, Free)) return
                            Free -> throw IllegalStateException("Only parker thread can set to free")
                            Unparked -> if (state.compareAndSet(Unparked, Free)) {
                                delegator.destroyRef(pd)
                                return
                            }
                        }
                    }
                }
                Unparked -> if (state.compareAndSet(Unparked, Free)) return
                is Parked -> throw IllegalStateException("Thread should not be able to call park when it is already parked")
                Unparking -> throw IllegalStateException("Thread should not be able to call park when it is already parked")
            }
        }
    }

    fun unpark() {
        while (true) {
            when (val currentState = state.value) { 
                Unparked -> return
                Unparking -> return
                Free -> if (state.compareAndSet(Free, Unparked)) return
                is Parked -> if (state.compareAndSet(currentState, Unparking)) {
                    delegator.wake(currentState.data)
                    if (!state.compareAndSet(Unparking, Unparked)) delegator.destroyRef(currentState.data)
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
private object Unparking : ParkingState

