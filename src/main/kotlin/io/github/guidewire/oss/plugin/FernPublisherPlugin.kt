package io.github.guidewire.oss.plugin

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
      extension.projectId.orNull?.let { task.projectId.set(it) }
      extension.reportPaths.orNull?.let { task.reportPaths.set(it) }
      extension.fernTags.orNull?.let { task.fernTags.set(it) }
      extension.verbose.orNull?.let { task.verbose.set(it) }
      extension.failOnError.orNull?.let {task.failOnError.set(it) }
      extension.authUrl.orNull?.let { task.authUrl.set(it) }
      extension.authClientId.orNull?.let { task.authClientId.set(it) }
      extension.authClientSecret.orNull?.let { task.authClientSecret.set(it) }
      extension.authScopes.orNull?.let { task.authScopes.set(it) }
      extension.apiEndpointPath.orNull?.let { task.apiEndpointPath.set(it) }
      task.projectDir.set(project.projectDir.absolutePath + "/")
    }
  }
}