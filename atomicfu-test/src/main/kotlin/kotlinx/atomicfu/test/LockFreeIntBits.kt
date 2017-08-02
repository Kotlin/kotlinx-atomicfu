package kotlinx.atomicfu.test

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

class LockFreeIntBits {
    private val bits = atomic(0)

    private fun Int.mask() = 1 shl this

    operator fun get(index: Int): Boolean = bits.value and index.mask() != 0

    // User-defined private inline function
    private inline fun bitUpdate(check: (Int) -> Boolean, upd: (Int) -> Int): Boolean {
        bits.update {
            if (check(it)) return false
            upd(it)
        }
        return true
    }

    fun bitSet(index: Int): Boolean {
        val mask = index.mask()
        return bitUpdate({ it and mask != 0 }, { it or mask })
    }

    fun bitClear(index: Int): Boolean {
        val mask = index.mask()
        return bitUpdate({ it and mask == 0 }, { it and mask.inv() })
    }
}