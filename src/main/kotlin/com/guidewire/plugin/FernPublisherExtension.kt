package com.guidewire.plugin

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

// Extension for plugin configuration
abstract class FernPublisherExtension {
  abstract val fernUrl: Property<String>
  abstract val projectName: Property<String>
  abstract val projectId: Property<String>
  abstract val reportPaths: ListProperty<String>
  abstract val fernTags: ListProperty<String>
  abstract val verbose: Property<Boolean>
  abstract val failOnError: Property<Boolean>

  init {
    fernTags.convention(listOf())
    verbose.convention(false)
    failOnError.convention(false)
    projectName.convention("")
  }
}