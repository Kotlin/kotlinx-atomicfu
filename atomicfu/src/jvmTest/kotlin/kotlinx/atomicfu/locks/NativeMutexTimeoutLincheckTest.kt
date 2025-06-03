package kotlinx.atomicfu.locks

import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck.util.LoggingLevel
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.nanoseconds

class NativeMutexTimeoutLincheckTest {
    class Counter {
        @Volatile
        private var value = 0

        fun inc(): Int = ++value
        fun get() = value
    }
    
    private val counter = Counter()
    private val localParkers = ConcurrentHashMap<ParkingHandle, ThreadParker>()

    private val lock by lazy {
        NativeMutex(
            park = { localParkers[ParkingSupport.currentThreadHandle()]!!.parkNanos(it.inWholeNanoseconds) },
            unpark = { localParkers[it]!!.unpark() }
        )
    }

    @Test
    fun modelCheckingTest(): Unit = ModelCheckingOptions()
        .iterations(2) // Change to 300 for exhaustive testing
        .invocationsPerIteration(5_000)
        .actorsBefore(1)
        .threads(3)
        .actorsPerThread(3)
        .actorsAfter(0)
        .hangingDetectionThreshold(100)
        .logLevel(LoggingLevel.INFO)
        .check(this::class.java)

    @Operation
    fun incNoTimeout() {
        localParkers.computeIfAbsent(ParkingSupport.currentThreadHandle()) { ThreadParker() }
        lock.lock()
        counter.inc()
        lock.unlock()
    }
    
    @Operation
    fun incTimeout() {
        localParkers.computeIfAbsent(ParkingSupport.currentThreadHandle()) { ThreadParker() }
        if (lock.tryLock(0.nanoseconds)) {
            counter.inc()
            lock.unlock()
        }
    }

    @Operation
    fun get() {
        localParkers.computeIfAbsent(ParkingSupport.currentThreadHandle()) { ThreadParker() }
        lock.lock()
        counter.get()
        lock.unlock()
    }
}