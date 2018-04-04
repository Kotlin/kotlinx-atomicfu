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

class SimpleLockTest {
    @Test
    fun withLock() {
        val lock = SimpleLock()
        val result = lock.withLock {
            "OK"
        }
        assertEquals("OK", result)
    }
}

class SimpleLock {
    private val _locked = atomic(0)

    fun <T> withLock(block: () -> T): T {
        // this contrieves construct triggers Kotlin compiler to reuse local variable slot #2 for
        // the exception in `finally` clause
        try {
            _locked.loop { locked ->
                check(locked == 0)
                if (!_locked.compareAndSet(0, 1)) return@loop // continue
                return block()
            }
        } finally {
            _locked.value = 0
        }
    }
}