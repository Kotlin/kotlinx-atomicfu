/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.transformer

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.math.abs

class FlowAnalyzer(
    private val start: AbstractInsnNode?
) {
    private var cur: AbstractInsnNode? = null

    // the depth at which our atomic variable lies now (zero == top of stack),
    // this ref at one slot below it (and we can choose to merge them!)
    private var depth = 0

    private fun abort(msg: String): Nothing = abort(msg, cur)
    private fun unsupported(): Nothing = abort("Unsupported operation on atomic variable")
    private fun unrecognized(i: AbstractInsnNode): Nothing = abort("Unrecognized operation ${i.toText()}")

    private fun push(n: Int, forward: Boolean) {
        if (!forward && abs(depth) < n) unsupported()
        depth += n
    }

    private fun pop(n: Int, forward: Boolean) {
        if (forward && depth < n) unsupported()
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

    // returns instruction preceding pushing arguments to the atomic factory
    fun getInitStart(): AbstractInsnNode {
        var i = start
        depth = -1
        while (i != null) {
            executeOne(i, false)
            if (depth == 0) return i
            i = i.previous
        }
        abort("Backward flow control falls after the beginning of the method")
    }

    fun getValueArgInitLast(): AbstractInsnNode {
        var i = start
        val valueArgSize = Type.getArgumentTypes((start as MethodInsnNode).desc)[0].size
        depth = -1
        while(i != null) {
            executeOne(i, false)
            i = i.previous
            if (depth == -valueArgSize) return i
        }
        abort("Backward flow control falls after the beginning of the method")
    }

    // forward is true when instructions are executed in forward order from top to bottom
    private fun executeOne(i: AbstractInsnNode, forward: Boolean = true): AbstractInsnNode? {
        when (i) {
            is LabelNode -> { /* ignore */ }
            is LineNumberNode -> { /* ignore */ }
            is FrameNode -> { /* ignore */ }
            is MethodInsnNode -> {
                popDesc(i.desc, forward)
                if (i.opcode == INVOKEVIRTUAL && depth == 0) return i // invoke virtual on atomic field ref
                if (i.opcode != INVOKESTATIC) pop(1, forward)
                pushDesc(i.desc, forward)
            }
            is FieldInsnNode -> when (i.opcode) {
                GETSTATIC -> pushDesc(i.desc, forward)
                PUTSTATIC -> popDesc(i.desc, forward)
                GETFIELD -> {
                    pop(1, forward)
                    pushDesc(i.desc, forward)
                }
                PUTFIELD -> {
                    popDesc(i.desc, forward)
                    pop(1, forward)
                }
                else -> unrecognized(i)
            }
            is MultiANewArrayInsnNode -> {
                pop(i.dims, forward)
                push(1, forward)
            }
            is LdcInsnNode -> {
                when (i.cst) {
                    is Double -> push(2, forward)
                    is Long -> push(2, forward)
                    else -> push(1, forward)
                }
            }
            else -> when (i.opcode) {
                ASTORE -> {
                    if (depth == 0) return i // stored atomic field ref
                    pop(1, forward) // stored something else
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
                    push(1, forward)
                }
                ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5, BIPUSH, SIPUSH -> {
                    push(1, forward)
                }
                LCONST_0, LCONST_1 -> {
                    push(2, forward)
                }
                FCONST_0, FCONST_1, FCONST_2 -> {
                    push(1, forward)
                }
                DCONST_0, DCONST_1 -> {
                    push(2, forward)
                }
                ILOAD, FLOAD, ALOAD -> {
                    push(1, forward)
                }
                LLOAD, DLOAD -> {
                    push(2, forward)
                }
                IALOAD, BALOAD, CALOAD, SALOAD -> {
                    pop(2, forward)
                    push(1, forward)
                }
                LALOAD, D2L -> {
                    pop(2, forward)
                    push(2, forward)
                }
                FALOAD -> {
                    pop(2, forward)
                    push(1, forward)
                }
                DALOAD, L2D -> {
                    pop(2, forward)
                    push(2, forward)
                }
                AALOAD -> {
                    pop(1, forward)
                    push(1, forward)
                }
                ISTORE, FSTORE -> {
                    pop(1, forward)
                }
                LSTORE, DSTORE -> {
                    pop(2, forward)
                }
                IASTORE, BASTORE, CASTORE, SASTORE, FASTORE, AASTORE -> {
                    pop(3, forward)
                }
                LASTORE, DASTORE -> {
                    pop(4, forward)
                }
                POP, MONITORENTER, MONITOREXIT -> {
                    pop(1, forward)
                }
                POP2 -> {
                    pop(2, forward)
                }
                DUP -> {
                    pop(1, forward)
                    push(2, forward)
                }
                DUP_X1 -> {
                    if (depth <= 1) unsupported()
                    push(1, forward)
                }
                DUP_X2 -> {
                    if (depth <= 2) unsupported()
                    push(1, forward)
                }
                DUP2 -> {
                    pop(2, forward)
                    push(4, forward)
                }
                DUP2_X1 -> {
                    if (depth <= 2) unsupported()
                    push(2, forward)
                }
                DUP2_X2 -> {
                    if (depth <= 3) unsupported()
                    push(2, forward)
                }
                SWAP -> {
                    if (depth <= 1) unsupported()
                }
                IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR, L2I, D2I, FCMPL, FCMPG -> {
                    pop(2, forward)
                    push(1, forward)
                }
                LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR -> {
                    pop(4, forward)
                    push(2, forward)
                }
                FADD, FSUB, FMUL, FDIV, FREM, L2F, D2F -> {
                    pop(2, forward)
                    push(1, forward)
                }
                DADD, DSUB, DMUL, DDIV, DREM -> {
                    pop(4, forward)
                    push(2, forward)
                }
                LSHL, LSHR, LUSHR -> {
                    pop(3, forward)
                    push(2, forward)
                }
                INEG, FNEG, I2B, I2C, I2S, IINC -> {
                    pop(1, forward)
                    push(1, forward)
                }
                LNEG, DNEG -> {
                    pop(2, forward)
                    push(2, forward)
                }
                I2L, F2L -> {
                    pop(1, forward)
                    push(2, forward)
                }
                I2F -> {
                    pop(1, forward)
                    push(1, forward)
                }
                I2D, F2D -> {
                    pop(1, forward)
                    push(2, forward)
                }
                F2I, ARRAYLENGTH, INSTANCEOF -> {
                    pop(1, forward)
                    push(1, forward)
                }
                LCMP, DCMPL, DCMPG -> {
                    pop(4, forward)
                    push(1, forward)
                }
                NEW -> {
                    push(1, forward)
                }
                NEWARRAY, ANEWARRAY -> {
                    pop(1, forward)
                    push(1, forward)
                }
                CHECKCAST -> {
                    /* nop for our needs */
                }
                else -> unrecognized(i)
            }
        }
        return null
    }

    private fun popDesc(desc: String, forward: Boolean) {
        when (desc[0]) {
            '(' -> {
                val types = Type.getArgumentTypes(desc)
                pop(types.indices.sumBy { types[it].size }, forward)
            }
            'J', 'D' -> pop(2, forward)
            else -> pop(1, forward)
        }
    }

    private fun pushDesc(desc: String, forward: Boolean) {
        val index = if (desc[0] == '(') desc.indexOf(')') + 1 else 0
        when (desc[index]) {
            'V' -> return
            'Z', 'C', 'B', 'S', 'I', 'F', '[', 'L' -> {
                push(1, forward)
            }
            'J', 'D' -> {
                push(2, forward)
            }
        }
    }
}