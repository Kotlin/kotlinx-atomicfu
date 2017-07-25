package kotlinx.atomicfu.transformer

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

val AbstractInsnNode.line: Int? get() {
    var cur = this
    while (true) {
        if (cur is LineNumberNode) return cur.line
        cur = cur.previous ?: return null
    }
}

fun AbstractInsnNode.atLine(): String {
    val line = line ?: return ""
    return "at line $line: "
}

fun AbstractInsnNode.atIndex(il: InsnList?): String {
    var cur = il?.first
    var index = 1
    while (cur != null && cur != this) {
        if (!cur.isUseless()) index++
        cur = cur.next
    }
    if (cur == null) return ""
    return "inst #$index: "
}

val AbstractInsnNode.nextUseful: AbstractInsnNode? get() {
    var cur = next
    while (cur.isUseless()) cur = cur.next
    return cur
}

private fun AbstractInsnNode?.isUseless() = this is LabelNode || this is LineNumberNode

inline fun MethodNode.forVarLoads(v: Int, block: (VarInsnNode) -> AbstractInsnNode?) {
    var cur = instructions.first
    while (cur != null) {
        if (cur is VarInsnNode && cur.opcode == Opcodes.ALOAD && cur.`var` == v) {
            cur = block(cur)
        } else
            cur = cur.next
    }
}

