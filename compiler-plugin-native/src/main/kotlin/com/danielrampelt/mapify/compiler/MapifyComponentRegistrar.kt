package com.danielrampelt.mapify.compiler

import com.danielrampelt.mapify.compiler.ir.MapifyLoweringExtension
import com.google.auto.service.AutoService
import com.intellij.mock.MockProject
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(ComponentRegistrar::class)
class MapifyComponentRegistrar : ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        messageCollector.report(CompilerMessageSeverity.INFO, "Mapify plugin is active", null)
        IrGenerationExtension.registerExtension(project, MapifyLoweringExtension(messageCollector, configuration))
    }
}