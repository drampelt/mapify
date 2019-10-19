package com.danielrampelt.mapify.compiler.jvm

import org.jetbrains.kotlin.backend.common.descriptors.allParameters
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.DelegatingClassBuilder
import org.jetbrains.kotlin.codegen.TransformationMethodVisitor
import org.jetbrains.kotlin.codegen.asmType
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.types.typeUtil.isByte
import org.jetbrains.kotlin.types.typeUtil.isChar
import org.jetbrains.kotlin.types.typeUtil.isDouble
import org.jetbrains.kotlin.types.typeUtil.isFloat
import org.jetbrains.kotlin.types.typeUtil.isInt
import org.jetbrains.kotlin.types.typeUtil.isLong
import org.jetbrains.kotlin.types.typeUtil.isShort
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.InsnList
import org.jetbrains.org.objectweb.asm.tree.InsnNode
import org.jetbrains.org.objectweb.asm.tree.IntInsnNode
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.VarInsnNode

internal class MapifyClassBuilder(
    private val messageCollector: MessageCollector,
    private val delegateBuilder: ClassBuilder,
    private val bindingContext: BindingContext,
    private val typeMapper: KotlinTypeMapper
) : DelegatingClassBuilder() {
    override fun getDelegate(): ClassBuilder = delegateBuilder

    override fun newMethod(
        origin: JvmDeclarationOrigin,
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val original = super.newMethod(origin, access, name, desc, signature, exceptions)
        val function = origin.descriptor as? FunctionDescriptor ?: return original
        if (!function.annotations.hasAnnotation(FqName("com.danielrampelt.mapify.Mapper"))) return original
        messageCollector.report(CompilerMessageSeverity.INFO, "Generating mapper for function: $function")

        val receiver = function.extensionReceiverParameter ?: return original
            .also { messageCollector.report(CompilerMessageSeverity.ERROR, "Mapper input can currently only be receiver parameters") }
        val receiverType = receiver.type
        val receiverTypeAsm = receiverType.asmType(typeMapper)
        val receiverIndex = function.allParameters.indexOf(receiver)

        val returnType = function.returnType ?: return original
            .also { messageCollector.report(CompilerMessageSeverity.ERROR, "Mapper must have a return type") }

        val returnTypeAsm = returnType.asmType(typeMapper)
        messageCollector.report(CompilerMessageSeverity.INFO, "Return type: ${returnTypeAsm.className}")
        val returnTypeClass = returnType.constructor.declarationDescriptor as? LazyClassDescriptor
        if (returnTypeClass == null || !returnTypeClass.isData) {
            messageCollector.report(CompilerMessageSeverity.ERROR, "Mapper must return a kotlin data class")
            return original
        }
        // TODO: either replace constructor arguments or find copy method instead
        val returnTypePrimaryConstructor = returnTypeClass.unsubstitutedPrimaryConstructor!!

        return object : TransformationMethodVisitor(original, access, name, desc, signature, exceptions) {
            override fun performTransformations(methodNode: MethodNode) {
                val ret = methodNode.instructions.last?.findInsnBefore { it.opcode == Opcodes.ARETURN }
                    ?: return messageCollector.report(CompilerMessageSeverity.ERROR, "Could not find return in mapper function")
                val invokeSpecial = ret.findInsnBefore { it.opcode == Opcodes.INVOKESPECIAL && it is MethodInsnNode && it.owner == returnTypeAsm.internalName } as? MethodInsnNode
                    ?: return messageCollector.report(CompilerMessageSeverity.ERROR, "Could not find constructor for mapper return type: ${returnTypeAsm.className}")
                val flagNode = invokeSpecial.previous?.findInsnBefore { it.opcode in Opcodes.ICONST_0..Opcodes.ICONST_5 || it.opcode in Opcodes.BIPUSH..Opcodes.SIPUSH || it.opcode == Opcodes.LDC }
                val initFlags = when {
                    flagNode == null -> null
                    flagNode.opcode in Opcodes.ICONST_0..Opcodes.ICONST_5 -> flagNode.opcode - Opcodes.ICONST_0
                    flagNode is IntInsnNode -> flagNode.operand
                    flagNode is LdcInsnNode -> flagNode.cst as? Int
                    else -> null
                }
                messageCollector.report(CompilerMessageSeverity.INFO, "Got flags: $initFlags")

                val copyInstructions = InsnList()
                var copyFlags = 0

                var currentParam = 1
                var copyDescriptor = "(${returnTypeAsm.descriptor}"
                returnTypePrimaryConstructor.valueParameters.forEachIndexed { index, parameter ->
                    var skip = initFlags != null && (currentParam and initFlags) == 0
                    val type = parameter.type
                    val typeAsm = type.asmType(typeMapper)
                    copyDescriptor += typeAsm.descriptor
                    messageCollector.report(CompilerMessageSeverity.INFO, "Parameter $index: $parameter (Skip: $skip - $currentParam)")

                    if (!skip) {
                        val parameterName = parameter.name.identifier
                        val getterName = "get${parameterName.substring(0, 1).toUpperCaseAsciiOnly()}${parameterName.substring(1)}"
                        // TODO: prioritize getters over fields

                        val matchingMember = DescriptorUtils.getAllDescriptors(receiverType.memberScope).firstOrNull { member ->
                            if (member !is CallableDescriptor) return@firstOrNull false
                            val nameMatches = member.name.identifier == parameterName || member.name.identifier == getterName
                            val typeMatches = member.returnType == type
                            // TODO: handle primitive boxing
                            nameMatches && typeMatches
                        }

                        if ((matchingMember is FunctionDescriptor && matchingMember.valueParameters.isEmpty()) || (matchingMember is PropertyDescriptor && matchingMember.getter != null)) {
                            messageCollector.report(CompilerMessageSeverity.INFO, "Got property or getter $matchingMember")
                            val functionName = if (matchingMember is FunctionDescriptor) matchingMember.name.identifier else getterName
                            copyInstructions.add(VarInsnNode(Opcodes.ALOAD, receiverIndex))
                            copyInstructions.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, receiverTypeAsm.internalName, functionName, "()${typeAsm.descriptor}", false))
                        } else {
                            messageCollector.report(CompilerMessageSeverity.INFO, "Could not find matching property or getter for parameter $parameterName, skipping")
                            skip = true
                        }
                    }

                    if (skip) {
                        copyFlags = copyFlags or currentParam
                        val opCode = when {
                            type.isByte() || type.isChar() || type.isShort() || type.isInt() || type.isBoolean() -> Opcodes.ICONST_0
                            type.isLong() -> Opcodes.LCONST_0
                            type.isFloat() -> Opcodes.FCONST_0
                            type.isDouble() -> Opcodes.DCONST_0
                            else -> Opcodes.ACONST_NULL
                        }
                        copyInstructions.add(InsnNode(opCode))
                    }

                    currentParam = currentParam shl 1
                }

                copyDescriptor += "ILjava/lang/Object;)${returnTypeAsm.descriptor}"

                copyInstructions.add(LdcInsnNode(copyFlags))
                copyInstructions.add(InsnNode(Opcodes.ACONST_NULL))
                copyInstructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, returnTypeAsm.internalName, "copy\$default", copyDescriptor, false))

                methodNode.instructions.insert(invokeSpecial, copyInstructions)

                messageCollector.report(CompilerMessageSeverity.INFO, "Copy flags: $copyFlags, desc: $copyDescriptor")
            }
        }
    }

    private fun AbstractInsnNode.findInsnBefore(predicate: (AbstractInsnNode) -> Boolean): AbstractInsnNode? {
        var currentNode: AbstractInsnNode? = this
        while (currentNode != null) {
            if (predicate.invoke(currentNode)) {
                return currentNode
            }
            currentNode = currentNode.previous
        }
        return null
    }
}
