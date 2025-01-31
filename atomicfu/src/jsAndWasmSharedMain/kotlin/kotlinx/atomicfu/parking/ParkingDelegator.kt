package kotlinx.atomicfu.parking

internal actual class ParkingData

internal actual object ParkingDelegator {
    actual fun createRef() = ParkingData()
    actual fun wait(ref: ParkingData) {}
    actual fun timedWait(ref: ParkingData, nanos: Long) {}
    actual fun wake(ref: ParkingData) {}
    actual fun destroyRef(ref: ParkingData) {}
}