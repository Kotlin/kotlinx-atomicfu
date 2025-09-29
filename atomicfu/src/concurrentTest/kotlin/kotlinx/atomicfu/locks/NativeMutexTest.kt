package kotlinx.atomicfu.locks

import kotlin.test.Test
import kotlin.test.assertEquals

private const val SLEEP_MILLIS = 10L
private const val N_REPEATS_SLOW = 100
private const val N_REPEATS_FAST = 30_000 

class NativeMutexTest {
    
    @Test
    fun testNativeMutexSlow() = testNativeMutex(SLEEP_MILLIS, N_REPEATS_SLOW)

    @Test
    fun testNativeMutexFast() = testNativeMutex(0, N_REPEATS_FAST)
    
    private fun testNativeMutex(sleepDuration: Long, iterations: Int) {
        val mutex = NativeMutex()
        val resultList = mutableListOf<String>()
        
        fun addResults(id: String) {
            repeat(iterations) { i ->
                mutex.lock()
                resultList.add("$id$i")
                if (sleepDuration > 0) sleepMillis(sleepDuration)
                resultList.add("$id$i")
                mutex.unlock()
            }
        }

        val futA = Fut { addResults("a") }
        val futB = Fut { addResults("b") }

        addResults("c")

        futA.waitThrowing()
        futB.waitThrowing()

        resultList.filterIndexed { i, _ -> i % 2 == 0 }
            .zip(resultList.filterIndexed { i, _ -> i % 2 == 1 }) { a, b ->
                assertEquals(a, b)
            }
    }
}