package com.danielrampelt.mapify.compiler.jvm

import org.jetbrains.kotlin.codegen.optimization.common.findPreviousOrNull
import org.jetbrains.kotlin.codegen.optimization.common.isMeaningful
import org.jetbrains.kotlin.codegen.optimization.removeNodeGetNext
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.InsnList
import org.jetbrains.org.objectweb.asm.tree.IntInsnNode
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicVerifier
import kotlin.math.pow

class ConstructorCall(
    val methodNode: MethodNode,
    internalClassName: String,
    owner: String
) {
    val invokeNode: MethodInsnNode
    val arguments: Array<Pair<AbstractInsnNode, AbstractInsnNode>?>
    var dataInitFlags: Int?

    init {
        val instructions = methodNode.instructions
        val lastInsn = instructions.last ?: throw IllegalStateException("Method has no instructions")
        invokeNode = lastInsn.findPreviousOrNull {
            it.opcode == Opcodes.INVOKESPECIAL && it is MethodInsnNode && it.owner == internalClassName && it.name == "<init>"
        } as? MethodInsnNode ?: throw IllegalStateException("Constructor for $internalClassName is never called")
        val invokeNodeIndex = instructions.indexOf(invokeNode)

        val flagNode = invokeNode.findPreviousOrNull { it.opcode in Opcodes.ICONST_0..Opcodes.ICONST_5 || it.opcode in Opcodes.BIPUSH..Opcodes.SIPUSH || it.opcode == Opcodes.LDC }
        dataInitFlags = when {
            flagNode == null -> null
            flagNode.opcode in Opcodes.ICONST_0..Opcodes.ICONST_5 -> flagNode.opcode - Opcodes.ICONST_0
            flagNode is IntInsnNode -> flagNode.operand
            flagNode is LdcInsnNode -> flagNode.cst as? Int
            else -> null
        }

        val frames = Analyzer<BasicValue>(BasicVerifier()).analyze(owner, methodNode)
        val argCount = frames[invokeNodeIndex].stackSize - frames[invokeNodeIndex + 1].stackSize
        arguments = arrayOfNulls(argCount - 1) // Ignore first arg since it's just the DUP call

        var currentArg = argCount - 1
        var endInsn: AbstractInsnNode? = null
        for (i in (invokeNodeIndex - 1) downTo 0) {
            val diff = frames[i + 1].stackSize - frames[invokeNodeIndex + 1].stackSize
            if (diff == argCount) continue

            if (endInsn == null) endInsn = instructions[i + 1]

            if (diff <= currentArg) {
                if (!instructions[i].isMeaningful) continue

                arguments[currentArg - 1] = Pair(instructions[i + 1], endInsn!!)
                currentArg -= 1
                endInsn = instructions[i]

                if (currentArg == 0 && instructions[i].opcode == Opcodes.DUP) break
            }
        }
    }

    fun shouldSkip(index: Int): Boolean {
        val dataInitFlags = dataInitFlags ?: return false
        val argFlag = 2.0.pow(index).toInt()
        return (dataInitFlags and argFlag) == 0
    }

    fun replaceArgument(index: Int, instructions: InsnList) {
        val argument = arguments[index] ?: throw IllegalStateException("Argument not found")
        val (startNode, endNode) = argument
        val beforeStartNode = startNode.previous

        var currentNode: AbstractInsnNode = startNode
        while (currentNode != endNode) {
            currentNode = methodNode.instructions.removeNodeGetNext(currentNode)!!
        }

        val next = methodNode.instructions.removeNodeGetNext(currentNode)!!
        methodNode.instructions.insertBefore(next, instructions)
        arguments[index] = Pair(beforeStartNode.next, next.previous)

        val initFlags = dataInitFlags
        if (index != arguments.size - 2 && initFlags != null) {
            val argFlag = 2.0.pow(index).toInt()
            if (initFlags and argFlag != 0) {
                dataInitFlags = initFlags xor argFlag
                replaceArgument(arguments.size - 2, InsnList().apply { add(LdcInsnNode(dataInitFlags!!)) })
            }
        }
    }
}
