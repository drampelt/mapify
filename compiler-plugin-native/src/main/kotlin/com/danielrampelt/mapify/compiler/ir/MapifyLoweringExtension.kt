package com.danielrampelt.mapify.compiler.ir

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly

class MapifyLoweringExtension(
    private val messageCollector: MessageCollector,
    private val configuration: CompilerConfiguration
) : IrGenerationExtension {
    override fun generate(file: IrFile, backendContext: BackendContext, bindingContext: BindingContext) {
        file.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitFunction(declaration: IrFunction) {
                if (!declaration.hasAnnotation(FqName("com.danielrampelt.mapify.Mapper"))) return super.visitFunction(declaration)
                val returnType = declaration.returnType.getClass() ?: return messageCollector.report(CompilerMessageSeverity.ERROR, "Mapper function has no return type")
                val returnIrType = declaration.returnType
                if (!returnType.isData) return messageCollector.report(CompilerMessageSeverity.ERROR, "Mapper must return a kotlin data class")

                val receiver = declaration.extensionReceiverParameter ?: return messageCollector.report(CompilerMessageSeverity.ERROR, "Mapper input can currently only be receiver parameters")
                val receiverClass = receiver.type.getClass() ?: return messageCollector.report(CompilerMessageSeverity.ERROR, "Mapper receiver can currently only be a class")

                declaration.transformChildrenVoid(object : IrElementTransformerVoid() {
                    override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                        if (expression.type != returnIrType) return super.visitConstructorCall(expression)

                        expression.descriptor.valueParameters.forEachIndexed { index, param ->
                            val skip = expression.getValueArgument(index) != null
                            messageCollector.report(CompilerMessageSeverity.INFO, "Parameter $index: $param (Skip: $skip)")
                            if (skip) return@forEachIndexed

                            val parameterName = param.name.identifier
                            val getterName = "get${parameterName.substring(0, 1).toUpperCaseAsciiOnly()}${parameterName.substring(1)}"

                            val allFunctions  = receiverClass.properties.filter { it.getter != null }.map { it.getter!! } + receiverClass.functions
                            val getter = allFunctions.firstOrNull { func ->
                                val nameMatches = func.name == param.name || func.name.asString() == getterName || (func.correspondingPropertySymbol?.owner?.name == param.name)
                                val typeMatches = param.type == func.returnType.toKotlinType()
                                nameMatches && typeMatches && func.valueParameters.isEmpty()
                            } ?: return@forEachIndexed messageCollector.report(CompilerMessageSeverity.INFO, "Could not find matching property or getter for parameter $parameterName, skipping")

                            backendContext.createIrBuilder(expression.symbol, expression.startOffset, expression.endOffset).apply {
                                val getterCall = irCall(getter)
                                getterCall.dispatchReceiver = irGet(getter.returnType, receiver.symbol)
                                expression.putValueArgument(index, getterCall)
                            }

                            messageCollector.report(CompilerMessageSeverity.INFO, "Got getter ${getter.descriptor}")
                        }
                        return super.visitConstructorCall(expression)
                    }
                })

                super.visitFunction(declaration)
            }
        })
    }
}
