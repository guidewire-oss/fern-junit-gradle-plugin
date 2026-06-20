package io.github.guidewire.oss.plugin

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

  // OAuth configuration
  abstract val authUrl: Property<String>
  abstract val authClientId: Property<String>
  abstract val authClientSecret: Property<String>
  abstract val authScopes: Property<String>

  // API endpoint path configuration
  abstract val apiEndpointPath: Property<String>

  init {
    fernTags.convention(listOf())
    verbose.convention(false)
    failOnError.convention(false)
    projectName.convention("")

    // OAuth defaults - empty means OAuth is disabled
    authUrl.convention("")
    authClientId.convention("")
    authClientSecret.convention("")
    authScopes.convention("")

    // API endpoint path default - use v1 API by default
    apiEndpointPath.convention("api/v1/test-runs")
  }
}