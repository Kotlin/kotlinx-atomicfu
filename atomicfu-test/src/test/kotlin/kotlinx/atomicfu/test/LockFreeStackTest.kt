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

import org.junit.Test

class LockFreeStackTest {
    @Test
    fun testClear() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushLoop("A")
        check(!s.isEmpty())
        s.clear()
        check(s.isEmpty())
    }

    @Test
    fun testPushPopLoop() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushLoop("A")
        check(!s.isEmpty())
        check(s.popLoop() == "A")
        check(s.isEmpty())
    }

    @Test
    fun testPushPopUpdate() {
        val s = LockFreeStack<String>()
        check(s.isEmpty())
        s.pushUpdate("A")
        check(!s.isEmpty())
        check(s.popUpdate() == "A")
        check(s.isEmpty())
    }
}
