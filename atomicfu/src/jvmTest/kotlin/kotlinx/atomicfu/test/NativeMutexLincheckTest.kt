import kotlinx.atomicfu.locks.NativeMutex
import kotlinx.atomicfu.parking.JvmParkingDelegator
import org.jetbrains.kotlinx.lincheck.LoggingLevel
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import kotlin.test.Test

class NativeMutexLincheckTest {

    private val lock = NativeMutex { JvmParkingDelegator() }
    private val mutableList = mutableListOf(0)

    @Test
    fun modelCheckingTest(): Unit = ModelCheckingOptions()
        .iterations(300)
        .invocationsPerIteration(10_000)
        .actorsBefore(1)
        .threads(3)
        .actorsPerThread(3)
        .actorsAfter(0)
        .hangingDetectionThreshold(100)
        .logLevel(LoggingLevel.INFO)
        .check(this::class.java)

    @Operation
    fun add(n: Int) {
        lock.lock()
        mutableList.add(n)
        lock.unlock()
    }

    @Operation
    fun removeFirst(): Int? {
        lock.lock()
        val bla =  mutableList.removeFirstOrNull()
        lock.unlock()
        return bla
    }

    @Operation
    fun removeLast(): Int? {
        lock.lock()
        val bla =  mutableList.removeLastOrNull()
        lock.unlock()
        return bla
    }
}
