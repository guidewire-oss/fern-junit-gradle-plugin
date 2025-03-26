package com.guidewire.plugin

import com.guidewire.parseReports
import com.guidewire.models.TestRun
import com.guidewire.sendTestRun
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
  abstract val projectName: Property<String>

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

  init {
    fernTags.convention(listOf()) // Set default value
    verbose.convention(false)
    description = "Publish test results to your Fern instance"
  }

  @TaskAction
  fun execute() {
    val isVerbose = verbose.get()
    val projectName = projectName.get()
    val fernUrl = (fernUrl.get() as String).removeSuffix("/")
    val tagsString = fernTags.get().joinToString(",")

    logger.lifecycle("Executing PublishToFern")
    logger.lifecycle("Project name: $projectName")
    logger.lifecycle("Publishing to Fern instance at: $fernUrl")

    if (reportPaths.get().isEmpty()) {
      logger.error("No report paths provided. Cannot publish results.")
      return
    }

    logger.lifecycle("Reading reports from: ${reportPaths.get().joinToString(":")}")
    // Create TestRun object
    val testRun = TestRun(
      testProjectName = projectName,
      testSeed = Random().nextLong()
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
          return
        }
      )
    }

    // Send the test run to Fern
    if (testRun.suiteRuns.isEmpty()) {
      logger.warn("No test suites found in the provided report paths. Nothing to publish.")
      return
    }

    logger.lifecycle("Found ${testRun.suiteRuns.size} test suites with a total of ${testRun.suiteRuns.sumOf { it.specRuns.size }} test specs")

    sendTestRun(testRun, fernUrl, isVerbose).fold(
      onSuccess = {
        logger.lifecycle("Successfully published test results to Fern")
      },
      onFailure = { error ->
        logger.error("Failed to publish test results to Fern: ${error.message}")
        if (isVerbose) {
          logger.error("Stack trace: ", error)
        }
        throw RuntimeException("Failed to publish test results to Fern", error)
      }
    )
  }
}