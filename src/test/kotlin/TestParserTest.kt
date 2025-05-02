import io.github.guidewire.oss.models.TestRun
import io.github.guidewire.oss.parseReports
import io.github.guidewire.oss.util.GlobalClock
import io.github.guidewire.oss.util.MockClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory

class TestParserTest {
  @TempDir
  lateinit var tempDir: Path

  private lateinit var testRun: TestRun
  private lateinit var fixedTime: ZonedDateTime

  @BeforeEach
  fun setup() {
    fixedTime = ZonedDateTime.parse("2023-01-01T10:00:00Z")
    GlobalClock.setInstance(MockClock(fixedTime))
    testRun = TestRun()
  }

  @Test
  fun `parseReports should handle empty file pattern`() {
    val result = parseReports(testRun = testRun, filePattern = "nonexistent/*.xml", projectDir = tempDir.absolutePathString(), verbose = true)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("No files found for pattern") ?: false)
  }

  @Test
  fun `parseReports should correctly parse a single hardcoded file path`() {
    // Create test XML file
    val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Sample Test Suite" tests="2" failures="0" errors="0" skipped="0" 
                       timestamp="2023-01-01T09:00:00Z" time="1.5">
                <testcase name="Test One" time="0.5"/>
                <testcase name="Test Two" time="1.0"/>
            </testsuite>
        """.trimIndent()

    val testFile = tempDir.resolve("test-report.xml")
    Files.write(testFile, xmlContent.toByteArray())

    // Parse the report
    val result = parseReports(testRun = testRun, filePattern = testFile.parent.toString() + "/test-report.xml", tags = "tag1,tag2", verbose = true)
    println(result.exceptionOrNull()?.printStackTrace())

    assertThat(result.isSuccess).isTrue()
    assertThat(testRun.suiteRuns).hasSize(1)
    assertThat(testRun.suiteRuns[0].suiteName).isEqualTo("Sample Test Suite")
    assertThat(testRun.suiteRuns[0].specRuns).hasSize(2)
    assertThat(testRun.suiteRuns[0].specRuns[0].specDescription).isEqualTo("Test One")
    assertThat(testRun.suiteRuns[0].specRuns[0].status).isEqualTo("passed")
    assertThat(testRun.suiteRuns[0].specRuns[0].tags).hasSize(2)
    assertThat(testRun.suiteRuns[0].specRuns[0].tags[0].name).isEqualTo("tag1")
    assertThat(testRun.suiteRuns[0].specRuns[0].tags[1].name).isEqualTo("tag2")
  }

  @Test
  fun `parseReports should correctly parse file a wildcard file path`() {
    // Create 2 test XML files
    val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Sample Test Suite" tests="2" failures="0" errors="0" skipped="0" 
                       timestamp="2023-01-01T09:00:00Z" time="1.5">
                <testcase name="Test One" time="0.5"/>
                <testcase name="Test Two" time="1.0"/>
            </testsuite>
        """.trimIndent()

    val testFile = tempDir.resolve("test-report.xml")
    Files.write(testFile, xmlContent.toByteArray())

    val xmlContent2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Extra Sample Test Suite" tests="2" failures="0" errors="0" skipped="0" 
                       timestamp="2023-01-01T09:00:00Z" time="1.5">
                <testcase name="Test Three" time="0.5"/>
                <testcase name="Test Four" time="1.0"/>
            </testsuite>
        """.trimIndent()

    val testFile2 = tempDir.resolve("test-report2.xml")
    Files.write(testFile2, xmlContent2.toByteArray())

    // Parse the report
    val result = parseReports(testRun = testRun, filePattern = testFile.parent.toString() + "/*.xml", tags = "tag1,tag2", verbose = true)
    println(result.exceptionOrNull()?.printStackTrace())

    // parsed all files
    assertThat(result.isSuccess).isTrue()
    assertThat(testRun.suiteRuns.size).isEqualTo(2)

    //parsed all suites
    val suitNames = testRun.suiteRuns.map { it.suiteName }.toSet()
    assertThat(suitNames).containsAll(listOf("Extra Sample Test Suite", "Sample Test Suite"))

    //parsed all runs
    val specDescriptions = testRun.suiteRuns.flatMap { suiteRun ->
      suiteRun.specRuns.map {
        it.specDescription
      }
    }.toSet()

    assertThat(specDescriptions.size).isEqualTo(4)
    assertThat(specDescriptions).containsAll(listOf("Test One", "Test Two", "Test Three", "Test Four"))
  }

  @Test
  fun `parseReports should correctly parse file double wildcard file path`() {
    // Create 2 test XML files
    val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Sample Test Suite" tests="2" failures="0" errors="0" skipped="0" 
                       timestamp="2023-01-01T09:00:00Z" time="1.5">
                <testcase name="Test One" time="0.5"/>
                <testcase name="Test Two" time="1.0"/>
            </testsuite>
        """.trimIndent()

    val testFile = tempDir.resolve("test-report.xml")
    Files.write(testFile, xmlContent.toByteArray())

    val xmlContent2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Extra Sample Test Suite" tests="2" failures="0" errors="0" skipped="0" 
                       timestamp="2023-01-01T09:00:00Z" time="1.5">
                <testcase name="Test Three" time="0.5"/>
                <testcase name="Test Four" time="1.0"/>
            </testsuite>
        """.trimIndent()

    val testFile2 = tempDir.resolve("test-report2.xml")
    Files.write(testFile2, xmlContent2.toByteArray())

    // Parse the report
    val result = parseReports(testRun = testRun, filePattern = testFile.parent.toString() + "/**/*.xml", tags = "tag1,tag2", verbose = true)
    println(result.exceptionOrNull()?.printStackTrace())

    // parsed all files
    assertThat(result.isSuccess).isTrue()
    assertThat(testRun.suiteRuns.size).isEqualTo(2)

    //parsed all suites
    val suitNames = testRun.suiteRuns.map { it.suiteName }.toSet()
    assertThat(suitNames).containsAll(listOf("Extra Sample Test Suite", "Sample Test Suite"))

    //parsed all runs
    val specDescriptions = testRun.suiteRuns.flatMap { suiteRun ->
      suiteRun.specRuns.map {
        it.specDescription
      }
    }.toSet()

    assertThat(specDescriptions.size).isEqualTo(4)
    assertThat(specDescriptions).containsAll(listOf("Test One", "Test Two", "Test Three", "Test Four"))
  }

  @Test
  fun `parseReports should correctly parse files in multiple sub directories`() {
    // Create 2 test XML files
    val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Sample Test Suite" tests="2" failures="0" errors="0" skipped="0" 
                       timestamp="2023-01-01T09:00:00Z" time="1.5">
                <testcase name="Test One" time="0.5"/>
                <testcase name="Test Two" time="1.0"/>
            </testsuite>
        """.trimIndent()

    val testFile = tempDir.resolve("test-report.xml")
    Files.write(testFile, xmlContent.toByteArray())

    val xmlContent2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Extra Sample Test Suite" tests="2" failures="0" errors="0" skipped="0" 
                       timestamp="2023-01-01T09:00:00Z" time="1.5">
                <testcase name="Test Three" time="0.5"/>
                <testcase name="Test Four" time="1.0"/>
            </testsuite>
        """.trimIndent()

    Files.createDirectory(tempDir.resolve("extra"))
    val testFile2 = tempDir.resolve("extra/test-report2.xml")
    Files.write(testFile2, xmlContent2.toByteArray())

    // Parse the report
    val result = parseReports(testRun = testRun, filePattern = testFile.parent.toString() + "/**/*.xml", tags = "tag1,tag2", verbose = true)
    println(result.exceptionOrNull()?.printStackTrace())

    // parsed all files
    assertThat(result.isSuccess).isTrue()
    assertThat(testRun.suiteRuns.size).isEqualTo(2)

    //parsed all suites
    val suitNames = testRun.suiteRuns.map { it.suiteName }.toSet()
    assertThat(suitNames).containsAll(listOf("Extra Sample Test Suite", "Sample Test Suite"))

    //parsed all runs
    val specDescriptions = testRun.suiteRuns.flatMap { suiteRun ->
      suiteRun.specRuns.map {
        it.specDescription
      }
    }.toSet()

    assertThat(specDescriptions.size).isEqualTo(4)
    assertThat(specDescriptions).containsAll(listOf("Test One", "Test Two", "Test Three", "Test Four"))
  }

  @Test
  fun `parseReports should correctly parse valid XML reports`() {
    // Create test XML file
    val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Sample Test Suite" tests="2" failures="0" errors="0" skipped="0" 
                       timestamp="2023-01-01T09:00:00Z" time="1.5">
                <testcase name="Test One" time="0.5"/>
                <testcase name="Test Two" time="1.0"/>
            </testsuite>
        """.trimIndent()

    val testFile = tempDir.resolve("test-report.xml")
    Files.write(testFile, xmlContent.toByteArray())

    // Parse the report
    val result = parseReports(testRun = testRun, filePattern = testFile.parent.toString() + "/**/*.xml", tags = "tag1,tag2", verbose = true)

    println(result.exceptionOrNull()?.printStackTrace())

    assertThat(result.isSuccess).isTrue()
    assertThat(testRun.suiteRuns).hasSize(1)
    assertThat(testRun.suiteRuns[0].suiteName).isEqualTo("Sample Test Suite")
    assertThat(testRun.suiteRuns[0].specRuns).hasSize(2)
    assertThat(testRun.suiteRuns[0].specRuns[0].specDescription).isEqualTo("Test One")
    assertThat(testRun.suiteRuns[0].specRuns[0].status).isEqualTo("passed")
    assertThat(testRun.suiteRuns[0].specRuns[0].tags).hasSize(2)
    assertThat(testRun.suiteRuns[0].specRuns[0].tags[0].name).isEqualTo("tag1")
    assertThat(testRun.suiteRuns[0].specRuns[0].tags[1].name).isEqualTo("tag2")
  }

  @Test
  fun `parseReports should handle failed tests`() {
    val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Failed Suite" tests="2" failures="1" errors="0" skipped="0" time="1.5">
                <testcase name="Passing Test" time="0.5"/>
                <testcase name="Failing Test" time="1.0">
                    <failure message="Assertion failed">Test failed due to XYZ</failure>
                </testcase>
            </testsuite>
        """.trimIndent()

    val testFile = tempDir.resolve("failed-report.xml")
    Files.write(testFile, xmlContent.toByteArray())

    val result = parseReports(testRun = testRun, filePattern = testFile.absolutePathString(), tags = "", verbose = true)

    println(result.exceptionOrNull()?.printStackTrace())

    assertThat(result.isSuccess).isTrue();
    assertThat(testRun.suiteRuns).hasSize(1);
    assertThat(testRun.suiteRuns[0].specRuns).hasSize(2);
    assertThat(testRun.suiteRuns[0].specRuns[0].status).isEqualTo("passed");
    assertThat(testRun.suiteRuns[0].specRuns[1].status).isEqualTo("failed");
    assertThat(testRun.suiteRuns[0].specRuns[1].message).contains("Assertion failed");
  }

  @Test
  fun `parseReports should handle skipped tests`() {
    val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Skipped Suite" tests="2" failures="0" errors="0" skipped="1" time="1.5">
                <testcase name="Normal Test" time="0.5"/>
                <testcase name="Skipped Test" time="0.0">
                    <skipped/>
                </testcase>
            </testsuite>
        """.trimIndent()

    val testFile = tempDir.resolve("skipped-report.xml")
    Files.write(testFile, xmlContent.toByteArray())

    val result = parseReports(testRun = testRun, filePattern = testFile.toString(), verbose = true)

    assertThat(result.isSuccess).isTrue()
    assertThat(testRun.suiteRuns[0].specRuns[0].status).isEqualTo("passed");
    assertThat(testRun.suiteRuns[0].specRuns[1].status).isEqualTo("skipped");
  }

  @Test
  fun `parseReports should correctly calculate times`() {
    val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Time Suite" timestamp="2023-01-01T09:00:00" time="1.5">
                <testcase name="Test One" time="0.5"/>
                <testcase name="Test Two" time="1.0"/>
            </testsuite>
        """.trimIndent()

    val testFile = tempDir.resolve("time-report.xml")
    Files.write(testFile, xmlContent.toByteArray())

    val result = parseReports(testRun = testRun, filePattern = testFile.toString(), verbose = true)

    assertThat(result.isSuccess).isTrue();

    val startTime = ZonedDateTime.parse("2023-01-01T09:00:00Z");
    assertThat(testRun.startTime?.format(DateTimeFormatter.ISO_INSTANT)).isEqualTo(startTime.format(DateTimeFormatter.ISO_INSTANT));
    assertThat(testRun.endTime?.format(DateTimeFormatter.ISO_INSTANT)).isEqualTo(startTime.plusSeconds(1).plus(500, ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_INSTANT));
  }
}