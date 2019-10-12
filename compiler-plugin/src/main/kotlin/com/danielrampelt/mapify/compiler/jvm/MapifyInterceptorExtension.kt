package com.danielrampelt.mapify.compiler.jvm

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin

internal class MapifyInterceptorExtension(
    private val messageCollector: MessageCollector,
    private val compilerConfiguration: CompilerConfiguration
) : ClassBuilderInterceptorExtension {
    override fun interceptClassBuilderFactory(
        interceptedFactory: ClassBuilderFactory,
        bindingContext: BindingContext,
        diagnostics: DiagnosticSink
    ): ClassBuilderFactory = object : ClassBuilderFactory by interceptedFactory {
        override fun newClassBuilder(origin: JvmDeclarationOrigin): ClassBuilder {
            val typeMapper = KotlinTypeMapper(
                bindingContext,
                interceptedFactory.classBuilderMode,
                "", // TODO: this seems to work but how do we get the module name?
                compilerConfiguration.languageVersionSettings
            )
            return MapifyClassBuilder(
                messageCollector,
                interceptedFactory.newClassBuilder(origin),
                bindingContext,
                typeMapper
            )
        }
    }
}