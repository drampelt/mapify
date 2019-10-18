package com.danielrampelt.mapify.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor

@AutoService(CommandLineProcessor::class)
class MapifyCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "mapify"
    override val pluginOptions: Collection<AbstractCliOption> = listOf()
}
