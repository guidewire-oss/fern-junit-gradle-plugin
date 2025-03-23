package com.guidewire

import com.guidewire.models.*
import org.w3c.dom.Element
import org.xml.sax.InputSource
import com.guidewire.util.GlobalClock
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.xml.parsers.DocumentBuilderFactory

fun parseReports(testRun: TestRun, filePattern: String, tags: String, verbose: Boolean): Result<Unit> {
  return runCatching {
    val matcher = FileSystems.getDefault().getPathMatcher("glob:$filePattern")
    val directoryPath = Path.of(File(filePattern).parent ?: ".")
    val files = File(directoryPath.toString()).walkTopDown()
      .filter { file -> matcher.matches(Path.of(file.absolutePath)) && file.isFile }
      .toList()

    //Require throws when false
    require(files.isNotEmpty()) { "No files found for pattern $filePattern" }

    files.forEach { file ->
      val suiteRunsResult = parseReport(file.absolutePath, tags, verbose)
      suiteRunsResult.getOrThrow().forEach { suiteRun ->
        testRun.suiteRuns.add(suiteRun)
      }
    }

    testRun.suiteRuns.forEach { suiteRun ->
      // Set testRun.startTime to the earliest suite start time
      if (testRun.startTime == null || suiteRun.startTime.isBefore(testRun.startTime)) {
        testRun.startTime = suiteRun.startTime
      }

      // Set testRun.endTime to the latest suite end time
      if (testRun.endTime == null || suiteRun.endTime.isAfter(testRun.endTime)) {
        testRun.endTime = suiteRun.endTime
      }
    }

    if (verbose) {
      println("TestRun start time: ${testRun.startTime}")
      println("TestRun end time: ${testRun.endTime}")
    }
  }
}

fun parseReport(filePath: String, tags: String, verbose: Boolean): Result<List<SuiteRun>> {
  return runCatching {

    val suiteRuns = mutableListOf<SuiteRun>()

    if (verbose) {
      println("Reading $filePath")
    }

    val file = File(filePath)
    val content = file.readText()

    if (verbose) {
      println("Parsing $filePath")
    }

    // Create XML document builder
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(InputSource(content.reader()))

    val root = doc.documentElement
    val testSuites = mutableListOf<TestSuite>()

    // Check if root is testsuites or testsuite
    if (root.tagName == "testsuites") {
      val suiteElements = root.getElementsByTagName("testsuite")
      for (i in 0 until suiteElements.length) {
        val suiteElement = suiteElements.item(i) as Element
        testSuites.add(parseTestSuiteElement(suiteElement))
      }
    } else if (root.tagName == "testsuite") {
      testSuites.add(parseTestSuiteElement(root))
    }

    for (suite in testSuites) {
      val run = parseTestSuite(suite, tags, verbose).getOrThrow()
      suiteRuns.add(run)
    }

    suiteRuns
  }
}

private fun parseTestSuiteElement(element: Element): TestSuite {
  val testCases = mutableListOf<TestCase>()
  val caseElements = element.getElementsByTagName("testcase")

  for (i in 0 until caseElements.length) {
    val caseElement = caseElements.item(i) as Element
    val failures = caseElement.getElementsByTagName("failure")
    val errors = caseElement.getElementsByTagName("error")
    val skips = caseElement.getElementsByTagName("skipped")

    val testCase = TestCase(
      name = caseElement.getAttribute("name"),
      time = caseElement.getAttribute("time"),
      failures = (0 until failures.length).map { j ->
        val failure = failures.item(j) as Element
        TestCase.Failure(
          message = failure.getAttribute("message"),
          content = failure.textContent
        )
      },
      errors = (0 until errors.length).map { j ->
        val error = errors.item(j) as Element
        TestCase.Error(
          message = error.getAttribute("message"),
          content = error.textContent
        )
      },
      skips = if (skips.length > 0) listOf(TestCase.Skip()) else emptyList()
    )
    testCases.add(testCase)
  }

  return TestSuite(
    name = element.getAttribute("name"),
    timestamp = element.getAttribute("timestamp"),
    time = element.getAttribute("time"),
    testCases = testCases
  )
}

fun parseTestSuite(testSuite: TestSuite, tags: String, verbose: Boolean): Result<SuiteRun> {
  return runCatching {
    if (verbose) {
      println("Parsing TestSuite ${testSuite.name}")
    }

    val suiteRun = SuiteRun(suiteName = testSuite.name)

    suiteRun.startTime = if (testSuite.timestamp.isEmpty()) {
      GlobalClock.now()
    } else {
      try {
        ZonedDateTime.parse(testSuite.timestamp, DateTimeFormatter.ISO_DATE_TIME)
      } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Failed to parse suite start time: ${e.message}")
      }
    }

    suiteRun.endTime = getEndTime(suiteRun.startTime, testSuite.time).getOrThrow()

    if (verbose) {
      println("Resulting SuiteRun: $suiteRun")
    }

    parseTestCases(suiteRun, testSuite, verbose, tags)

    suiteRun
  }
}

private fun parseTestCases(suiteRun: SuiteRun, testSuite: TestSuite, verbose: Boolean, tags: String) {
  var startTime = suiteRun.startTime
  var endTime: ZonedDateTime

  for (testCase in testSuite.testCases) {
    if (verbose) {
      println("Parsing TestCase ${testCase.name}")
    }

    val status = when {
      testCase.failures.isNotEmpty() -> "failed"
      testCase.errors.isNotEmpty() -> "failed"
      testCase.skips.isNotEmpty() -> "skipped"
      else -> "passed"
    }

    val message = when {
      testCase.failures.isNotEmpty() ->
        "${testCase.failures[0].message}\n${testCase.failures[0].content}"

      testCase.errors.isNotEmpty() ->
        "${testCase.errors[0].message}\n${testCase.errors[0].content}"

      else -> ""
    }

    endTime = getEndTime(startTime, testCase.time).getOrThrow()

    val specRun = SpecRun(
      specDescription = testCase.name,
      status = status,
      message = message,
      tags = convertToTags(tags),
      startTime = startTime,
      endTime = endTime
    )

    suiteRun.specRuns.add(specRun)
    startTime = endTime

    if (verbose) {
      println("Resulting SpecRun: $specRun")
    }
  }
}

fun getEndTime(startTime: ZonedDateTime, durationSeconds: String): Result<ZonedDateTime> {
  return runCatching {
    val duration = durationSeconds.toDoubleOrNull()
      ?: throw IllegalArgumentException("Invalid duration format: $durationSeconds")

    val milliseconds = (duration * 1000).toLong()
    startTime.plus(milliseconds, ChronoUnit.MILLIS)
    startTime
  }
}

fun convertToTags(tagString: String): List<Tag> {
  return tagString.split(",").filter { it.isNotEmpty() }.map { Tag(name = it) }
}