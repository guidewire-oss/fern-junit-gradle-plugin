import com.guidewire.models.TestRun
import com.guidewire.parseReports
import com.guidewire.util.GlobalClock
import com.guidewire.util.MockClock
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

//TODO make tests better

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

    System.out.println(result.exceptionOrNull()?.printStackTrace())
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("No files found for pattern") ?: false)
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

    assertTrue(result.isSuccess)
    assertEquals(1, testRun.suiteRuns.size)
    assertEquals("Sample Test Suite", testRun.suiteRuns[0].suiteName)
    assertEquals(2, testRun.suiteRuns[0].specRuns.size)
    assertEquals("Test One", testRun.suiteRuns[0].specRuns[0].specDescription)
    assertEquals("passed", testRun.suiteRuns[0].specRuns[0].status)
    assertEquals(2, testRun.suiteRuns[0].specRuns[0].tags.size)
    assertEquals("tag1", testRun.suiteRuns[0].specRuns[0].tags[0].name)
    assertEquals("tag2", testRun.suiteRuns[0].specRuns[0].tags[1].name)
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

    assertTrue(result.isSuccess)
    assertEquals(1, testRun.suiteRuns.size)
    assertEquals(2, testRun.suiteRuns[0].specRuns.size)
    assertEquals("passed", testRun.suiteRuns[0].specRuns[0].status)
    assertEquals("failed", testRun.suiteRuns[0].specRuns[1].status)
    assertTrue(testRun.suiteRuns[0].specRuns[1].message.contains("Assertion failed"))
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

    assertTrue(result.isSuccess)
    assertEquals("passed", testRun.suiteRuns[0].specRuns[0].status)
    assertEquals("skipped", testRun.suiteRuns[0].specRuns[1].status)
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

    assertTrue(result.isSuccess)
    val startTime = ZonedDateTime.parse("2023-01-01T09:00:00Z")
    assertEquals(startTime.format(DateTimeFormatter.ISO_INSTANT), testRun.startTime?.format(DateTimeFormatter.ISO_INSTANT))
    assertEquals(startTime.plusSeconds(1).plus(500, ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_INSTANT), testRun.endTime?.format(DateTimeFormatter.ISO_INSTANT))
  }
}