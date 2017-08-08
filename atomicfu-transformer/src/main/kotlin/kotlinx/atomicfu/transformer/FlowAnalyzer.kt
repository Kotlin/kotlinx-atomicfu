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
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode

class FlowAnalyzer(
    private val start: AbstractInsnNode?
) {
    private var cur: AbstractInsnNode? = null

    // the depth at which our atomic variable lies now (zero == top of stack),
    // this ref at one slot below it (and we can choose to merge them!)
    private var depth = 0

    private fun abort(msg: String): Nothing = abort(msg, cur)
    private fun unsupported(): Nothing = abort("Unsupported operation on atomic variable")
    private fun unrecognized(): Nothing = abort("Unrecognized operation")

    private fun push(n: Int) { depth += n }

    private fun pop(n: Int) {
        if (depth < n) unsupported()
        depth -= n
    }

    // with stack top containing transformed variables analyses the following sequential data flow until consumed with:
    //   * "astore" -- result is VarInsnNode
    //   * "invokevirtual" on it -- result is MethodInsnNode
    // All other outcomes produce transformation error
    fun execute(): AbstractInsnNode {
        var i = start
        while (i != null) {
            cur = i
            executeOne(i)?.let { return it }
            i = i.next
        }
        abort("Flow control falls after the end of the method")
    }

    private fun executeOne(i: AbstractInsnNode): AbstractInsnNode? {
        when (i) {
            is MethodInsnNode -> {
                popDesc(i.desc)
                if (i.opcode == INVOKEVIRTUAL && depth == 0) return i // invoke virtual on atomic field ref
                if (i.opcode != INVOKESTATIC) pop(1)
                pushDesc(i.desc)
            }
            is FieldInsnNode -> when (i.opcode) {
                GETSTATIC -> pushDesc(i.desc)
                PUTSTATIC -> popDesc(i.desc)
                GETFIELD -> {
                    pop(1)
                    pushDesc(i.desc)
                }
                PUTFIELD -> {
                    popDesc(i.desc)
                    pop(1)
                }
                else -> unrecognized()
            }
            is MultiANewArrayInsnNode -> {
                pop(i.dims)
                push(1)
            }
            else -> when (i.opcode) {
                ASTORE -> {
                    if (depth == 0) return i // stored atomic field ref
                    pop(1) // stored something else
                }
                NOP -> { /* nop */ }
                GOTO, TABLESWITCH, LOOKUPSWITCH, ATHROW, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL,
                IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE -> {
                    abort("Unsupported branching/control within atomic operation")
                }
                IRETURN, FRETURN, ARETURN, RETURN, LRETURN, DRETURN -> {
                    abort("Unsupported return within atomic operation")
                }
                ACONST_NULL -> {
                    push(1)
                }
                ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5, BIPUSH, SIPUSH -> {
                    push(1)
                }
                LCONST_0, LCONST_1 -> {
                    push(2)
                }
                FCONST_0, FCONST_1, FCONST_2 -> {
                    push(1)
                }
                DCONST_0, DCONST_1 -> {
                    push(2)
                }
                ILOAD, FLOAD, ALOAD -> {
                    push(1)
                }
                LLOAD, DLOAD -> {
                    push(2)
                }
                IALOAD, BALOAD, CALOAD, SALOAD -> {
                    pop(2)
                    push(1)
                }
                LALOAD, D2L -> {
                    pop(2)
                    push(2)
                }
                FALOAD -> {
                    pop(2)
                    push(1)
                }
                DALOAD, L2D -> {
                    pop(2)
                    push(2)
                }
                AALOAD -> {
                    pop(1)
                    push(1)
                }
                ISTORE, FSTORE -> {
                    pop(1)
                }
                LSTORE, DSTORE -> {
                    pop(2)
                }
                IASTORE, BASTORE, CASTORE, SASTORE, FASTORE, AASTORE -> {
                    pop(3)
                }
                LASTORE, DASTORE -> {
                    pop(4)
                }
                POP, MONITORENTER, MONITOREXIT -> {
                    pop(1)
                }
                POP2 -> {
                    pop(2)
                }
                DUP -> {
                    pop(1)
                    push(2)
                }
                DUP_X1 -> {
                    if (depth <= 1) unsupported()
                    push(1)
                }
                DUP_X2 -> {
                    if (depth <= 2) unsupported()
                    push(1)
                }
                DUP2 -> {
                    pop(2)
                    push(4)
                }
                DUP2_X1 -> {
                    if (depth <= 2) unsupported()
                    push(2)
                }
                DUP2_X2 -> {
                    if (depth <= 3) unsupported()
                    push(2)
                }
                SWAP -> {
                    if (depth <= 1) unsupported()
                }
                IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR, L2I, D2I, FCMPL, FCMPG -> {
                    pop(2)
                    push(1)
                }
                LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR -> {
                    pop(4)
                    push(2)
                }
                FADD, FSUB, FMUL, FDIV, FREM, L2F, D2F -> {
                    pop(2)
                    push(1)
                }
                DADD, DSUB, DMUL, DDIV, DREM -> {
                    pop(4)
                    push(2)
                }
                LSHL, LSHR, LUSHR -> {
                    pop(3)
                    push(2)
                }
                INEG, FNEG, I2B, I2C, I2S, IINC -> {
                    pop(1)
                    push(1)
                }
                LNEG, DNEG -> {
                    pop(2)
                    push(2)
                }
                I2L, F2L -> {
                    pop(1)
                    push(2)
                }
                I2F -> {
                    pop(1)
                    push(1)
                }
                I2D, F2D -> {
                    pop(1)
                    push(2)
                }
                F2I, ARRAYLENGTH, INSTANCEOF -> {
                    pop(1)
                    push(1)
                }
                LCMP, DCMPL, DCMPG -> {
                    pop(4)
                    push(1)
                }
                NEW -> {
                    push(1)
                }
                NEWARRAY, ANEWARRAY -> {
                    pop(1)
                    push(1)
                }
                CHECKCAST -> {
                    /* nop for our needs */
                }
                else -> unrecognized()
            }
        }
        return null
    }

    private fun popDesc(desc: String) {
        when (desc[0]) {
            '(' -> {
                val types = Type.getArgumentTypes(desc)
                pop(types.indices.sumBy { types[it].size })
            }
            'J', 'D' -> pop(2)
            else -> pop(1)
        }
    }

    private fun pushDesc(desc: String) {
        val index = if (desc[0] == '(') desc.indexOf(')') + 1 else 0
        when (desc[index]) {
            'V' -> return
            'Z', 'C', 'B', 'S', 'I', 'F', '[', 'L' -> {
                push(1)
            }
            'J', 'D' -> {
                push(2)
            }
        }
    }
}