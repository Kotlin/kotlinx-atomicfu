package kotlinx.atomicfu.locks

import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.time.Duration

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
    private val slot = atomic<Pair<ParkingHandle, T>?>(null)
    fun exchange(item: T): T {
        val myPair = Pair(ParkingSupport.currentThreadHandle(), item)
        if (slot.compareAndSet(null, myPair)) {
            while (slot.value == myPair) ParkingSupport.park(Duration.INFINITE)
            val waiterPair = slot.value!!
            slot.value = null
            ParkingSupport.unpark(waiterPair.first)
            return waiterPair.second!!
        } else {
            val waiterPair = slot.getAndSet(myPair)
            ParkingSupport.unpark(waiterPair!!.first)
            while (slot.value == myPair) ParkingSupport.park(Duration.INFINITE)
            return waiterPair.second
        }
    }
}