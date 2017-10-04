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

import kotlinx.atomicfu.atomic

class LongArithmetic {
    private val _x = atomic(0L)
    val x get() = _x.value

    fun lazySet(v: Long) = _x.lazySet(v)
    fun getAndSet(v: Long) = _x.getAndSet(v)
    fun getAndIncrement() = _x.getAndIncrement()
    fun getAndDecrement() = _x.getAndDecrement()
    fun getAndAdd(v: Long) = _x.getAndAdd(v)
    fun addAndGet(v: Long) = _x.addAndGet(v)
    fun incrementAndGet() = _x.incrementAndGet()
    fun decrementAndGet() = _x.decrementAndGet()
}