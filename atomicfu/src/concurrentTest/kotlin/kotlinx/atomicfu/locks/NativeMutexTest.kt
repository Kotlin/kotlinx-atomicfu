package kotlinx.atomicfu.locks

import kotlin.test.Test
import kotlin.test.assertEquals

private const val SLEEP_MILLIS = 10L
private const val N_REPEATS_SLOW = 100
private const val N_REPEATS_FAST = 30_000 

class NativeMutexTest {
    
    
    @Test
    fun testNativeMutexSlow() {
        val mutex = NativeMutex()
        val resultList = mutableListOf<String>()

        val futA = Fut {  
            repeat(N_REPEATS_SLOW) { i ->
                mutex.lock()
                resultList.add("a$i")
                sleepMillis(SLEEP_MILLIS)
                resultList.add("a$i")
                mutex.unlock()
            }
        }

        val futB = Fut {
            repeat(N_REPEATS_SLOW) { i ->
                mutex.lock()
                resultList.add("b$i")
                sleepMillis(SLEEP_MILLIS)
                resultList.add("b$i")
                mutex.unlock()
            }
        }

        repeat(N_REPEATS_SLOW) { i ->
            mutex.lock()
            resultList.add("c$i")
            sleepMillis(SLEEP_MILLIS)
            resultList.add("c$i")
            mutex.unlock()
        }
        
        futA.waitThrowing()
        futB.waitThrowing()
        
        resultList.filterIndexed { i, _ -> i % 2 == 0 }
            .zip(resultList.filterIndexed { i, _ -> i % 2 == 1 }) { a, b ->
            assertEquals(a, b)
        }
    }

    @Test
    fun testNativeMutexFast() {
        val mutex = SynchronousMutex()
        val resultList = mutableListOf<String>()

        val fut1 = testThread {
            repeat(N_REPEATS_FAST) { i -> 
                mutex.lock()
                resultList.add("a$i")
                resultList.add("a$i")
                mutex.unlock()
            }
        }

        val fut2 = testThread {
            repeat(N_REPEATS_FAST) { i -> 
                mutex.lock()
                resultList.add("b$i")
                resultList.add("b$i")
                mutex.unlock()
            }
        }

        repeat(N_REPEATS_FAST) { i ->
            mutex.lock()
            resultList.add("c$i")
            resultList.add("c$i")
            mutex.unlock()
        }
        fut1.join()
        fut2.join()
        
        resultList
            .filterIndexed { i, _ -> i % 2 == 0 }
            .zip(resultList.filterIndexed {i, _ -> i % 2 == 1}) { a, b ->
            assertEquals(a, b)
        }
    }
}