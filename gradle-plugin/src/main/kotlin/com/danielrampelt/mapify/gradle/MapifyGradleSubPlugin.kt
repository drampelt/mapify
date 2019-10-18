package com.danielrampelt.mapify.gradle

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinGradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@AutoService(KotlinGradleSubplugin::class)
class MapifyGradleSubPlugin : KotlinGradleSubplugin<AbstractCompile> {
    override fun isApplicable(project: Project, task: AbstractCompile): Boolean =
        project.plugins.hasPlugin(MapifyGradlePlugin::class.java)

    override fun getCompilerPluginId() = "mapify"

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact("com.danielrampelt.mapify", "compiler-plugin", "0.1.0-SNAPSHOT")
    }

    override fun getNativeCompilerPluginArtifact(): SubpluginArtifact? {
        return SubpluginArtifact("com.danielrampelt.mapify", "compiler-plugin-native", "0.1.0-SNAPSHOT")
    }

    override fun apply(
        project: Project,
        kotlinCompile: AbstractCompile,
        javaCompile: AbstractCompile?,
        variantData: Any?,
        androidProjectHandler: Any?,
        kotlinCompilation: KotlinCompilation<KotlinCommonOptions>?
    ): List<SubpluginOption> {
        return emptyList()
    }
}