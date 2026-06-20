package io.github.guidewire.oss.plugin

import io.github.guidewire.oss.parseReports
import io.github.guidewire.oss.models.TestRun
import io.github.guidewire.oss.sendTestRun
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.util.*

abstract class PublishToFern : DefaultTask() {
  @get:Input
  abstract val fernUrl: Property<String>

  @get:Input
  @get:Optional
  abstract val projectName: Property<String>

  @get:Input
  @get:Optional
  abstract val projectId: Property<String>

  @get:Input
  abstract val reportPaths: ListProperty<String>

  @get:Input
  @get:Optional
  abstract val fernTags: ListProperty<String>

  @get:Input
  @get:Optional
  abstract val verbose: Property<Boolean>

  @get:Internal
  abstract val projectDir: Property<String>

  @get:Input
  @get:Optional
  abstract val failOnError: Property<Boolean>

  @get:Input
  @get:Optional
  abstract val authUrl: Property<String>

  @get:Input
  @get:Optional
  abstract val authClientId: Property<String>

  @get:Input
  @get:Optional
  abstract val authClientSecret: Property<String>

  @get:Input
  @get:Optional
  abstract val authScopes: Property<String>

  @get:Input
  @get:Optional
  abstract val apiEndpointPath: Property<String>

  init {
    fernTags.convention(listOf()) // Set default value
    verbose.convention(false)
    description = "Publish test results to your Fern instance"
    failOnError.convention(false)
    projectName.convention("")
    projectId.convention("")
    authUrl.convention("")
    authClientId.convention("")
    authClientSecret.convention("")
    authScopes.convention("")
    apiEndpointPath.convention("api/v1/test-runs")
  }

  @TaskAction
  fun execute() {
    val projectName = projectName.get()
    val projectId = projectId.get()

    if(projectName.isBlank() && projectId.isBlank()) {
      throw IllegalArgumentException("a projectId or a projectName must be specified")
    }

    val isVerbose = verbose.get()
    val fernUrl = (fernUrl.get() as String).removeSuffix("/")
    val tagsString = fernTags.get().joinToString(",")

    logger.lifecycle("Executing PublishToFern")
    if(projectName.isNotBlank()) {
      logger.lifecycle("Project name: $projectName")
    } else {
      logger.lifecycle("Project ID: $projectId")
    }
    logger.lifecycle("Publishing to Fern instance at: $fernUrl")

    if (reportPaths.get().isEmpty()) {
      logger.error("No report paths provided. Cannot publish results.")
      if(failOnError.get()) {
        throw RuntimeException("No report paths provided. Cannot publish results.")
      }

      return
    }

    logger.lifecycle("Reading reports from: ${reportPaths.get().joinToString(":")}")
    // Create TestRun object
    val testRun = TestRun(
      testProjectName = projectName,
      testProjectId = projectId,
      testSeed = Random().nextLong(0, Long.MAX_VALUE),
    )

    // Process each report path
    for (reportPath in reportPaths.get()) {
      if (isVerbose) {
        logger.debug("Processing report path: $reportPath")
      }

      // Parse reports
      parseReports(testRun, reportPath, projectDir.get(), tagsString, isVerbose).fold(
        onSuccess = {
          if (isVerbose) {
            logger.debug("Successfully parsed reports from $reportPath")
          }
        },
        onFailure = { error ->
          logger.error("Failed to parse reports from $reportPath: ${error.message}")
          if (isVerbose) {
            logger.error("Stack trace: ", error)
          }

          if(failOnError.get()) {
            throw error
          }
        }
      )
    }

    // Send the test run to Fern
    if (testRun.suiteRuns.isEmpty()) {
      logger.warn("No test suites found in the provided report paths. Nothing to publish.")
      return
    }

    logger.lifecycle("Found ${testRun.suiteRuns.size} test suites with a total of ${testRun.suiteRuns.sumOf { it.specRuns.size }} test specs")

    // Create OAuth config if auth URL is provided
    val oauthConfig = if (authUrl.get().isNotBlank()) {
      if (authClientId.get().isBlank() || authClientSecret.get().isBlank()) {
        logger.error("OAuth configuration error: authUrl is set but authClientId or authClientSecret is missing")
        if (failOnError.get()) {
          throw IllegalArgumentException("OAuth configuration error: authUrl is set but authClientId or authClientSecret is missing")
        }
        null
      } else {
        io.github.guidewire.oss.auth.OAuthConfig(
          tokenUrl = authUrl.get(),
          clientId = authClientId.get(),
          clientSecret = authClientSecret.get(),
          scopes = authScopes.get()
        )
      }
    } else {
      null
    }

    sendTestRun(
      testRun = testRun,
      fernUrl = fernUrl,
      verbose = isVerbose,
      oauthConfig = oauthConfig,
      apiEndpointPath = apiEndpointPath.get()
    ).fold(
      onSuccess = {
        logger.lifecycle("Successfully published test results to Fern")
      },
      onFailure = { error ->
        logger.error("Failed to publish test results to Fern: ${error.message}")
        if (isVerbose) {
          logger.error("Stack trace: ", error)
        }

        if(failOnError.get()) {
          throw RuntimeException("Failed to publish test results to Fern", error)
        }
      }
    )
  }
}