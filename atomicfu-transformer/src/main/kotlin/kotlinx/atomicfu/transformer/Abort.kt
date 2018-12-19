/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.transformer

import org.objectweb.asm.tree.AbstractInsnNode

class AbortTransform(
    message: String,
    val i: AbstractInsnNode? = null
) : Exception(message)

fun abort(message: String, i: AbstractInsnNode? = null): Nothing = throw AbortTransform(message, i)
