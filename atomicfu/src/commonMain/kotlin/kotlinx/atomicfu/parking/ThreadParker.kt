package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic

/**
 * This is defined in common to be testable with lincheck.
 * Should in practice never be used on jvm.
 */

internal class ThreadParker(private val delegator: ParkingDelegator) {
    private val state = atomic(STATE_FREE)
    private val atomicRef = atomic<Any?>(null)

    fun park() = parkWith { delegator.wait(atomicRef.value!!) }
    fun parkNanos(nanos: Long) = parkWith { 
        delegator.timedWait(atomicRef.value!!, nanos) 
        
        // If this fails it means the unpark call was before timout was reached
        // Than we need to make sure if the wake call has happened before we return and destroy the ptr
        // Therefore we call delegator.wait which should return immediately when wake call was already made.
        // If not it should still return fast (this should be a rare race condition).
        // This ensures the wake call has happened before cleanup.
        if (!state.compareAndSet(STATE_PARKED, STATE_FREE)) {
            delegator.wait(atomicRef.value!!)
        }
    }
    
    private fun parkWith(invokeWait: () -> Unit) {
        while (true) {
            when (val currentState = state.value) {

                STATE_FREE -> {
                    atomicRef.value = delegator.createRef()
                    // If state changed, cleanup and reiterate.
                    if (!state.compareAndSet(currentState, STATE_PARKED)) {
                        delegator.destroyRef(atomicRef.value!!)
                        atomicRef.value = null 
                        continue
                    }
                    invokeWait()
                    delegator.destroyRef(atomicRef.value!!)
                    atomicRef.value = null
                    return
                }

                STATE_UNPARKED -> {
                    if (!state.compareAndSet(currentState, STATE_FREE)) continue
                    return
                }

                STATE_PARKED ->
                    throw IllegalStateException("Thread should not be able to call park when it is already parked")

            }
        }
    }

    fun unpark() {
        while (true) {
            when (val currentState = state.value) {

                STATE_UNPARKED -> return

                STATE_FREE -> {
                    if (!state.compareAndSet(currentState, STATE_UNPARKED)) continue
                    return
                }

                STATE_PARKED -> {
                    if (!state.compareAndSet(currentState, STATE_FREE)) continue
                    delegator.wake(atomicRef.value!!)
                    return
                }
            }
        }
    }
}

private const val STATE_UNPARKED = 0
private const val STATE_FREE = 1
private val STATE_PARKED = 2
