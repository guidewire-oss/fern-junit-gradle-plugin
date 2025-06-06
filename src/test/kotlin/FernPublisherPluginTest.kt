import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.guidewire.oss.plugin.FernPublisherExtension
import io.github.guidewire.oss.plugin.PublishToFern
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class FernPublisherPluginTest {

  @TempDir
  lateinit var projectDir: Path

  private lateinit var buildFile: File
  private lateinit var testReportDir: File
  private lateinit var wireMockServer: WireMockServer

  @BeforeEach
  fun setup() {
    buildFile = projectDir.resolve("build.gradle.kts").toFile()
    testReportDir = projectDir.resolve("build/test-results/test").toFile()
    testReportDir.mkdirs()

    // Start mock server
    wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
    wireMockServer.start()
    configureFor("localhost", wireMockServer.port())

    // Stub the API endpoint
    stubFor(
      post(urlEqualTo("/api/testrun/"))
        .willReturn(aResponse().withStatus(200))
    )

    // Create sample test report
    val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="SampleTest" tests="2" failures="0" errors="0" skipped="0" time="1.234">
                <testcase name="testOne" time="0.5"/>
                <testcase name="testTwo" time="0.7"/>
            </testsuite>
        """.trimIndent()

    val reportFile = testReportDir.resolve("TEST-SampleTest.xml")
    Files.write(reportFile.toPath(), sampleXml.toByteArray())
  }

  @AfterEach
  fun tearDown() {
    wireMockServer.stop()
  }

  @Test
  fun `plugin should apply correctly to project`() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("io.github.guidewire-oss.fern-publisher")

    assertTrue(project.tasks.findByName("publishToFern") is PublishToFern)
  }

  @Test
  fun `plugin extension should configure task properties`() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("io.github.guidewire-oss.fern-publisher")

    project.extensions.configure<FernPublisherExtension>("fernPublisher") {
      it.fernUrl.set("http://example.com")
      it.projectName.set("test-project")
      it.reportPaths.set(listOf("build/reports/**/*.xml"))
      it.verbose.set(true)
    }

    val task = project.tasks.findByName("publishToFern") as PublishToFern

    assertThat(task.fernUrl.get()).isEqualTo("http://example.com")
    assertThat(task.projectName.get()).isEqualTo("test-project")
    assertThat(task.reportPaths.get()).isEqualTo(listOf("build/reports/**/*.xml"))
    assertThat(task.verbose.get()).isTrue()
  }

  @Test
  fun `task should successfully publish test results`() {
    buildFile.writeText(
      """
            plugins {
                id("io.github.guidewire-oss.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                projectName.set("test-project")
                reportPaths.set(listOf("build/test-results/test/**/*.xml"))
                verbose.set(true)
            }
        """.trimIndent()
    )

    val result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern", "--stacktrace")
      .build()

    // Verify task execution
    assertThat(result.output).contains("Executing PublishToFern")
    assertThat(result.output).contains("Successfully published test results to Fern")

    // Verify the API was called
    verify(
      postRequestedFor(urlEqualTo("/api/testrun/"))
        .withHeader("Content-Type", equalTo("application/json"))
    )
  }

  @Test
  fun `task should handle API errors`() {
    // Reset stub to return an error
    reset()
    stubFor(
      post(urlEqualTo("/api/testrun/"))
        .willReturn(aResponse().withStatus(500))
    )

    buildFile.writeText(
      """
            plugins {
                id("io.github.guidewire-oss.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                projectName.set("test-project")
                reportPaths.set(listOf("build/test-results/test/**/*.xml"))
                failOnError.set(true)
                verbose.set(true)
            }
        """.trimIndent()
    )

    val runner = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern")

    val result = runner.buildAndFail()

    assertThat(result.output).contains("Failed to publish test results to Fern")
  }

  @Test
  fun `task should handle missing report files`() {
    buildFile.writeText(
      """
            plugins {
                id("io.github.guidewire-oss.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                projectName.set("test-project")
                reportPaths.set(listOf("non-existent/**/*.xml"))
                failOnError.set(true)
                verbose.set(true)
            }
        """.trimIndent()
    )

    val runner = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern")

    val result = runner.buildAndFail()

    assertThat(result.output).contains("No files found")
  }

  @Test
  fun `task should fail when failOnError is true`() {
    buildFile.writeText(
      """
            plugins {
                id("io.github.guidewire-oss.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                projectName.set("test-project")
                reportPaths.set(listOf("build/test-results/fakePath/**/*.xml"))
                verbose.set(true)
                failOnError.set(true)
            }
        """.trimIndent()
    )

    val result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern", "--stacktrace")
      .buildAndFail()

    // Verify task execution
    assertThat(result.output).contains("Failed to parse reports")
  }

  @Test
  fun `task should NOT fail when failOnError is false but still log errors`() {
    buildFile.writeText(
      """
            plugins {
                id("io.github.guidewire-oss.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                projectName.set("test-project")
                reportPaths.set(listOf("build/test-results/fakePath/**/*.xml"))
                verbose.set(true)
            }
        """.trimIndent()
    )

    val result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern", "--stacktrace")
      .build()

    // Verify task execution
    assertThat(result.output).contains("Failed to parse reports")
  }

  @Test
  fun `task should fail when both projectName and projectID are not configured`() {
    buildFile.writeText(
      """
            plugins {
                id("io.github.guidewire-oss.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                reportPaths.set(listOf("build/test-results/fakePath/**/*.xml"))
                verbose.set(true)
                failOnError.set(true)
            }
        """.trimIndent()
    )

    val result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern", "--stacktrace")
      .buildAndFail()

    // Verify task execution
    assertThat(result.output).contains("a projectId or a projectName must be specified")
  }


  @ParameterizedTest
  @CsvSource(
    "projectName,",
    ",projectId",
    "projectName,projectId"
  )
  fun `task should accept either projectName, projectId, or both`(projectName: String?, projectId: String?) {

    buildFile.writeText(
      """
            plugins {
                id("io.github.guidewire-oss.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                projectName.set("${projectName}")
                projectId.set("${projectId}")
                reportPaths.set(listOf("build/test-results/test/**/*.xml"))
                verbose.set(true)
            }
        """.trimIndent()
    )

    val result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern", "--stacktrace")
      .build()

    // Verify task execution
    assertThat(result.output).contains("Executing PublishToFern")
    assertThat(result.output).contains("Successfully published test results to Fern")

    // Verify the API was called
    verify(
      postRequestedFor(urlEqualTo("/api/testrun/"))
        .withHeader("Content-Type", equalTo("application/json"))
    )
  }
}