package com.danielrampelt.mapify

import org.gradle.api.Plugin
import org.gradle.api.Project

class MapifyGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create("mapify", MapifyExtension::class.java)
    }
}

open class MapifyExtension {
    var enabled: Boolean = true
}