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

class MultiInitTest {
    @Test
    fun testBasic() {
        val t = MultiInit()
        check(t.incA() == 1)
        check(t.incA() == 2)
        check(t.incB() == 1)
        check(t.incB() == 2)
    }
}

class MultiInit {
    private val a = atomic(0)
    private val b = atomic(0)

    fun incA() = a.incrementAndGet()
    fun incB() = b.incrementAndGet()

    companion object {
        fun foo() {} // just to force some clinit in outer file
    }
}