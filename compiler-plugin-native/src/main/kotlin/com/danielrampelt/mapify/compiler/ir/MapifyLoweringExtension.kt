package com.danielrampelt.mapify.compiler.ir

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.resolve.BindingContext

class MapifyLoweringExtension(
    private val messageCollector: MessageCollector,
    private val configuration: CompilerConfiguration
) : IrGenerationExtension {
    override fun generate(file: IrFile, backendContext: BackendContext, bindingContext: BindingContext) {
        messageCollector.report(CompilerMessageSeverity.WARNING, "Processing file ${file.name}")
    }
}