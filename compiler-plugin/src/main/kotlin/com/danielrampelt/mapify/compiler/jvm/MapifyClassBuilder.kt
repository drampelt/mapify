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
import org.jetbrains.kotlin.descriptors.impl.AbstractClassDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.types.typeUtil.isInterface
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.InsnList
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
        val returnTypeClass = returnType.constructor.declarationDescriptor as? AbstractClassDescriptor
        if (returnTypeClass == null || !returnTypeClass.isData) {
            messageCollector.report(CompilerMessageSeverity.ERROR, "Mapper must return a kotlin data class")
            return original
        }
        // TODO: either replace constructor arguments or find copy method instead
        val returnTypePrimaryConstructor = returnTypeClass.unsubstitutedPrimaryConstructor!!

        return object : TransformationMethodVisitor(original, access, name, desc, signature, exceptions) {
            override fun performTransformations(methodNode: MethodNode) {
                val constructorCall = ConstructorCall(methodNode, returnTypeAsm.internalName, delegateBuilder.thisName)

                returnTypePrimaryConstructor.valueParameters.forEachIndexed { index, parameter ->
                    val skip = constructorCall.shouldSkip(index)
                    messageCollector.report(CompilerMessageSeverity.INFO, "Parameter $index: $parameter (Skip: $skip)")
                    if (skip) return@forEachIndexed

                    val type = parameter.type
                    val typeAsm = type.asmType(typeMapper)

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
                        val methodCall = InsnList()
                        methodCall.add(VarInsnNode(Opcodes.ALOAD, receiverIndex))
                        val opCode = if (receiverType.isInterface()) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
                        methodCall.add(MethodInsnNode(opCode, receiverTypeAsm.internalName, functionName, "()${typeAsm.descriptor}", receiverType.isInterface()))
                        constructorCall.replaceArgument(index, methodCall)
                    } else {
                        messageCollector.report(CompilerMessageSeverity.INFO, "Could not find matching property or getter for parameter $parameterName, skipping")
                    }
                }
            }
        }
    }
}
