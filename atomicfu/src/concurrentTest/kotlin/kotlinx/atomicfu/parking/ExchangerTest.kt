package kotlinx.atomicfu.parking

import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ExchangerTest {
    
    @Test
    fun exchangeTwoLists() {
        val aBefore = List(100_000) { 0 }
        val bBefore = List(100_000) { 1 }
        val aAfter = mutableListOf<Int>()
        val bAfter = mutableListOf<Int>()
        
        val exchanger = Exchanger<Int>()
        
        val at = testThread { 
            aBefore.forEachIndexed { i, v -> 
                val item = exchanger.exchange(v)
                aAfter.add(item)
            }
        }
        val bt = testThread {
            bBefore.forEachIndexed { i, v -> 
                val item = exchanger.exchange(v)
                bAfter.add(item)
            }
        }
        at.join()
        bt.join()
        assertContentEquals(aBefore, bAfter)
        assertContentEquals(bBefore, aAfter)
    }
}

internal class Exchanger<T> {
    private val slot = atomic<Pair<KThread, T>?>(null)
    fun exchange(item: T): T {
        if (slot.compareAndSet(null, Pair(KThread.currentThread(), item))) {
            Parker.park()
            val waiterPair = slot.value!!
            slot.value = null
            Parker.unpark(waiterPair.first)
            return waiterPair.second!!
        } else {
            val waiterPair = slot.getAndSet(Pair(KThread.currentThread(), item))
            Parker.unpark(waiterPair!!.first)
            Parker.park()
            return waiterPair.second
        }
    }
}