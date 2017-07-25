package kotlinx.atomicfu.transformer

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList

class AbortTransform(message: String) : Exception(message)

fun abort(message: String, i: AbstractInsnNode? = null, il: InsnList? = null): Nothing =
    throw AbortTransform("${i?.atLine()}${i?.atIndex(il)}$message")
