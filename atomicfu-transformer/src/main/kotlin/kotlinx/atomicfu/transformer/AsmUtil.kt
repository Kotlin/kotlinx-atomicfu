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

package kotlinx.atomicfu.transformer

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*

val AbstractInsnNode.line: Int? get() {
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

val AbstractInsnNode.nextUseful: AbstractInsnNode? get() {
    var cur: AbstractInsnNode? = next
    while (cur.isUseless()) cur = cur!!.next
    return cur
}

val AbstractInsnNode?.thisOrPrevUseful: AbstractInsnNode? get() {
    var cur: AbstractInsnNode? = this
    while (cur.isUseless()) cur = cur!!.previous
    return cur
}

private fun AbstractInsnNode?.isUseless() = this is LabelNode || this is LineNumberNode || this is FrameNode

fun InsnList.listUseful(limit: Int = Int.MAX_VALUE) : List<AbstractInsnNode> {
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

fun AbstractInsnNode.isAreturn() =
    this.opcode == ARETURN

fun AbstractInsnNode.isReturn() =
    this.opcode == RETURN

inline fun MethodNode.forVarLoads(v: Int, block: (VarInsnNode) -> AbstractInsnNode?) {
    var cur = instructions.first
    while (cur != null) {
        if (cur is VarInsnNode && cur.opcode == ALOAD && cur.`var` == v) {
            cur = block(cur)
        } else
            cur = cur.next
    }
}

