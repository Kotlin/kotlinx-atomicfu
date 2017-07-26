package kotlinx.atomicfu.transformer

import org.objectweb.asm.tree.AbstractInsnNode

class AbortTransform(
    message: String,
    val i: AbstractInsnNode? = null
) : Exception(message)

fun abort(message: String, i: AbstractInsnNode? = null): Nothing = throw AbortTransform(message, i)
