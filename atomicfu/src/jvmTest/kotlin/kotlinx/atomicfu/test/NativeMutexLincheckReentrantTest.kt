import kotlinx.atomicfu.locks.NativeMutex
import kotlinx.atomicfu.parking.JvmParkingDelegator
import org.jetbrains.kotlinx.lincheck.LoggingLevel
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import kotlin.test.Test
import kotlin.test.Ignore

class NativeMutexLincheckReentrantTest {

    private val lock = NativeMutex {JvmParkingDelegator()}
    private val mutableList = mutableListOf(0)

    @Ignore
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
        if (!lock.tryLock()) throw IllegalStateException("couldnt reent with trylock")
        if (!lock.tryLock()) throw IllegalStateException("couldnt reent with trylock")
        mutableList.add(n)
        lock.unlock()
        lock.unlock()
        lock.unlock()
    }

    @Operation
    fun removeFirst(): Int? {
        lock.lock()
        if (!lock.tryLock()) throw IllegalStateException("couldnt reent with trylock")
        if (!lock.tryLock()) throw IllegalStateException("couldnt reent with trylock")
        val bla =  mutableList.removeFirstOrNull()
        lock.unlock()
        lock.unlock()
        lock.unlock()
        return bla
    }

    @Operation
    fun removeLast(): Int? {
        lock.lock()
        lock.lock()
        val bla =  mutableList.removeLastOrNull()
        lock.unlock()
        lock.unlock()
        return bla
    }
}
