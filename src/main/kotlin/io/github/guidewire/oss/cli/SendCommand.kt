package io.github.guidewire.oss.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.guidewire.oss.models.TestRun
import io.github.guidewire.oss.parseReports
import io.github.guidewire.oss.sendTestRun
import kotlin.system.exitProcess

class SendCommand : CliktCommand(
  name = "send"
) {
  override fun help(context: Context): String {
    return "Send JUnit test reports to Fern"
  }

  private val fernUrl by option("-u", "--fern-url", help = "Base URL of the Fern Reporter instance to send test reports to").required()
  private val projectName by option("-n", "--project-name", help = "Name of the project to associate test reports with").required()
  private val projectId by option("-i", "--project-id", help = "ID of the project to associate test reports with").required()
  private val filePatterns: List<String> by option("-f", "--file-pattern", help = "File name pattern of test reports to send to Fern").multiple(required = true)
  private val tags by option("-t", "--tags", help = "Comma-separated tags to be included on runs")
  private val verbose by option("-v", "--verbose", help = "Enable verbose output").flag()

  override fun run() {
    try {
      echo("Reading reports from: ${filePatterns.joinToString(":")}")
      // Create TestRun object
      val testRun = TestRun(
        testProjectName = projectName,
        testSeed = System.currentTimeMillis(),
        testProjectId = projectId,
      )

      // Process each report path
      for (reportPath in filePatterns) {
        if (verbose) {
          echo("Processing report path: $reportPath")
        }

        // Parse reports
        parseReports(testRun, reportPath, "", tags ?: "", verbose).fold(
          onSuccess = {
            if (verbose) {
              echo("Successfully parsed reports from $reportPath")
            }
          },
          onFailure = { error ->
            echo("Failed to parse reports from $reportPath: ${error.message}", err = true)
            throw error
          }
        )
      }

      // Send the test run to Fern
      if (testRun.suiteRuns.isEmpty()) {
        echo("No test suites found in the provided report paths. Nothing to publish.")
        exitProcess(0)
      }

      echo("Found ${testRun.suiteRuns.size} test suites with a total of ${testRun.suiteRuns.sumOf { it.specRuns.size }} test specs")

      if (!fernUrl.startsWith("http")) {
        echo("ERROR: Fern URL must start with 'http' or 'https'", err = true)
        exitProcess(1)
      }

      sendTestRun(testRun, fernUrl, verbose).fold(
        onSuccess = {
          echo("Successfully published test results to Fern")
        },
        onFailure = { error ->
          throw RuntimeException("Failed to publish test results to Fern", error)
        }
      )
    } catch (e: Exception) {
      echo("ERROR: [${e::class.simpleName}] ${e.message}")
      if (verbose) {
        echo("Stack trace: $e", err = true)
      }
      exitProcess(1)
    }
  }
}