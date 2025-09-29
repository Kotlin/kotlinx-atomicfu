package kotlinx.atomicfu.locks

import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Validate
import org.jetbrains.lincheck.util.LoggingLevel
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test

class NativeMutexReentrantLincheckTest {
    class Counter {
        private var value = 0
        fun inc(): Int = ++value
        fun get() = value
    }
    
    private val counter = Counter()
    private val localParkers = ConcurrentHashMap<ParkingHandle, ThreadParker>()

    // Lazy prevents issue with lincheck not finding lambdas
    private val lock by lazy {
        NativeMutex(
            park = { localParkers[ParkingSupport.currentThreadHandle()]!!.park() },
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
    fun inc(): Int {
        localParkers.computeIfAbsent(ParkingSupport.currentThreadHandle()) { ThreadParker() }
        lock.lock()
        check(lock.tryLock()) {"couldn't reenter with trylock"}
        check(lock.tryLock()) {"couldn't reenter with trylock"}
        val result = counter.inc()
        lock.unlock()
        lock.unlock()
        lock.unlock()
        return result
    }

    @Operation
    fun get(): Int {
        localParkers.computeIfAbsent(ParkingSupport.currentThreadHandle()) { ThreadParker() }
        lock.lock()
        check(lock.tryLock()) {"couldn't reenter with trylock"}
        check(lock.tryLock()) {"couldn't reenter with trylock"}
        val result = counter.get()
        lock.unlock()
        lock.unlock()
        lock.unlock()
        return result
    }
    
    @Validate
    fun validate() {
        // Check queue is empty (only have head)
        check(lock.getQueueSize() == 1)
    }
}
