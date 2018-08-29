/*
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.test.*

class LockFreeIntBitsTest {
    @Test
    fun testBasic() {
        val bs = LockFreeIntBits()
        check(!bs[0])
        check(bs.bitSet(0))
        check(bs[0])
        check(!bs.bitSet(0))

        check(!bs[1])
        check(bs.bitSet(1))
        check(bs[1])
        check(!bs.bitSet(1))
        check(!bs.bitSet(0))

        check(bs[0])
        check(bs.bitClear(0))
        check(!bs.bitClear(0))

        check(bs[1])
    }
}

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