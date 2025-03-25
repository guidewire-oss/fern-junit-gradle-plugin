package com.guidewire.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class FernPublisherPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    // Register the extension for configuration
    val extension = project.extensions.create("fernPublisher", FernPublisherExtension::class.java)

    // Register the task
    project.tasks.register("publishToFern", PublishToFern::class.java) { task ->
      // Apply values from extension to task if they exist
      extension.fernUrl.orNull?.let { task.fernUrl.set(it) }
      extension.projectName.orNull?.let { task.projectName.set(it) }
      extension.reportPaths.orNull?.let { task.reportPaths.set(it) }
      extension.fernTags.orNull?.let { task.fernTags.set(it) }
      extension.verbose.orNull?.let { task.verbose.set(it) }
      task.projectDir.set(project.projectDir.absolutePath + "/")
    }
  }
}