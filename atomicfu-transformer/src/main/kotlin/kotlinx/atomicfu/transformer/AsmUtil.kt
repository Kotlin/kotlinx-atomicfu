/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.transformer

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.*
import org.objectweb.asm.tree.*
import org.objectweb.asm.util.*

val AbstractInsnNode.line: Int?
    get() {
        var cur = this
        while (true) {
            if (cur is LineNumberNode) return cur.line
            cur = cur.previous ?: return null
        }
    }

fun AbstractInsnNode.atIndex(insnList: InsnList?): String {
    var cur = insnList?.first
    var index = 1
    while (cur != null && cur != this) {
        if (!cur.isUseless()) index++
        cur = cur.next
    }
    if (cur == null) return ""
    return "inst #$index: "
}

val AbstractInsnNode.nextUseful: AbstractInsnNode?
    get() {
        var cur: AbstractInsnNode? = next
        while (cur.isUseless()) cur = cur!!.next
        return cur
    }

val AbstractInsnNode?.thisOrPrevUseful: AbstractInsnNode?
    get() {
        var cur: AbstractInsnNode? = this
        while (cur.isUseless()) cur = cur!!.previous
        return cur
    }

fun getInsnOrNull(from: AbstractInsnNode?, to: AbstractInsnNode?, predicate: (AbstractInsnNode) -> Boolean): AbstractInsnNode? {
    var cur: AbstractInsnNode? = from?.next
    while (cur != null && cur != to && !predicate(cur)) cur = cur.next
    return cur
}

private fun AbstractInsnNode?.isUseless() = this is LabelNode || this is LineNumberNode || this is FrameNode

fun InsnList.listUseful(limit: Int = Int.MAX_VALUE): List<AbstractInsnNode> {
    val result = ArrayList<AbstractInsnNode>(limit)
    var cur = first
    while (cur != null && result.size < limit) {
        if (!cur.isUseless()) result.add(cur)
        cur = cur.next
    }
    return result
}

fun AbstractInsnNode.isAload(index: Int) =
    this is VarInsnNode && this.opcode == ALOAD && this.`var` == index

fun AbstractInsnNode.isGetField(owner: String) =
    this is FieldInsnNode && this.opcode == GETFIELD && this.owner == owner

fun AbstractInsnNode.isGetStatic(owner: String) =
    this is FieldInsnNode && this.opcode == GETSTATIC && this.owner == owner

fun AbstractInsnNode.isGetFieldOrGetStatic() =
    this is FieldInsnNode && (this.opcode == GETFIELD || this.opcode == GETSTATIC)

fun AbstractInsnNode.isAreturn() =
    this.opcode == ARETURN

fun AbstractInsnNode.isReturn() =
    this.opcode == RETURN

fun AbstractInsnNode.isTypeReturn(type: Type) =
    opcode == when (type) {
        INT_TYPE -> IRETURN
        LONG_TYPE -> LRETURN
        BOOLEAN_TYPE -> IRETURN
        else -> ARETURN
    }

fun AbstractInsnNode.isInvokeVirtual() =
        this.opcode == INVOKEVIRTUAL

@Suppress("UNCHECKED_CAST")
fun MethodNode.localVar(v: Int, node: AbstractInsnNode): LocalVariableNode? =
    (localVariables as List<LocalVariableNode>).firstOrNull { it.index == v && verifyLocalVarScopeStart(v, node, it.start)}

// checks that the store instruction is followed by the label equal to the local variable scope start from the local variables table
private fun verifyLocalVarScopeStart(v: Int, node: AbstractInsnNode, scopeStart: LabelNode): Boolean {
    var i = node.next
    while (i != null) {
        // check that no other variable is stored into the same slot v before finding the scope start label
        if (i is VarInsnNode && i.`var` == v) return false
        if (i is LabelNode && i === scopeStart) return true
        i = i.next
    }
    return false
}

inline fun forVarLoads(v: Int, start: LabelNode, end: LabelNode, block: (VarInsnNode) -> AbstractInsnNode?) {
    var cur: AbstractInsnNode? = start
    while (cur != null && cur !== end) {
        if (cur is VarInsnNode && cur.opcode == ALOAD && cur.`var` == v) {
            cur = block(cur)
        } else
            cur = cur.next
    }
}

fun nextVarLoad(v: Int, start: AbstractInsnNode): VarInsnNode {
    var cur: AbstractInsnNode? = start
    while (cur != null) {
        when (cur.opcode) {
            GOTO, TABLESWITCH, LOOKUPSWITCH, ATHROW, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL,
            IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE,
            IRETURN, FRETURN, ARETURN, RETURN, LRETURN, DRETURN -> {
                abort("Unsupported branching/control while searching for load of spilled variable #$v", cur)
            }
            ALOAD -> {
                if ((cur as VarInsnNode).`var` == v) return cur
            }
        }
        cur = cur.next
    }
    abort("Flow control falls after the end of the method while searching for load of spilled variable #$v")
}

fun accessToInvokeOpcode(access: Int) =
    if (access and ACC_STATIC != 0) INVOKESTATIC else INVOKEVIRTUAL

fun AbstractInsnNode.toText(): String {
    val printer = Textifier()
    accept(TraceMethodVisitor(printer))
    return (printer.getText()[0] as String).trim()
}

val String.ownerPackageName get() = substringBeforeLast('/')
