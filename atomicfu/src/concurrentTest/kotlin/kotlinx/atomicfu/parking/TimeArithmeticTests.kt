package kotlinx.atomicfu.parking

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class TimeArithmeticTests {
    
    
    
    @Test
    fun timeArithmeticTest() {
        val currentTimes = listOf<Long>(
            31_560_000, // one year in seconds
            31_560_000 * 5,
            31_560_000 * 25,
            31_560_000 * 60,
            31_560_000 * 100,
            31_560_000 * 500,
            31_560_000 * 1000,
            31_560_000 * 10000,
            )
        
        currentTimes.forEach { currentTimeInSeconds -> 
            
            
            // Test Long
            repeat(1000) {
                val nanos = Random.nextLong()
                val updatedTime = currentTimeInSeconds.addNanosToSeconds(nanos)
                if (nanos > 0) assertTrue { updatedTime > currentTimeInSeconds }
                if (nanos < 0) assertTrue { updatedTime < currentTimeInSeconds }
            }

            // Test Int
            repeat(1000) {
                val currentTimeInInt = currentTimeInSeconds.toInt()
                val nanos = Random.nextLong()
                val updatedTime = currentTimeInInt.addNanosToSeconds(nanos)
                if (nanos > 0) assertTrue { updatedTime > currentTimeInSeconds && updatedTime <= Int.MAX_VALUE }
                if (nanos < 0) assertTrue { updatedTime < currentTimeInSeconds && updatedTime >= Int.MIN_VALUE }
            }
        }
    }
    
    
}