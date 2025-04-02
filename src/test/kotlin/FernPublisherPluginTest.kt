import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.guidewire.plugin.FernPublisherExtension
import com.guidewire.plugin.PublishToFern
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
    project.plugins.apply("com.guidewire.fern-publisher")

    assertTrue(project.tasks.findByName("publishToFern") is PublishToFern)
  }

  @Test
  fun `plugin extension should configure task properties`() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("com.guidewire.fern-publisher")

    project.extensions.configure<FernPublisherExtension>("fernPublisher") {
      it.fernUrl.set("http://example.com")
      it.projectName.set("test-project")
      it.reportPaths.set(listOf("build/reports/**/*.xml"))
    }

    val task = project.tasks.findByName("publishToFern") as PublishToFern
    assertEquals("http://example.com", task.fernUrl.get())
    assertEquals("test-project", task.projectName.get())
    assertEquals(listOf("build/reports/**/*.xml"), task.reportPaths.get())
  }

  @Test
  fun `task should successfully publish test results`() {
    buildFile.writeText(
      """
            plugins {
                id("com.guidewire.fern-publisher")
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
    assertTrue(result.output.contains("Executing PublishToFern"))
    assertTrue(result.output.contains("Successfully published test results to Fern"))

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
                id("com.guidewire.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                projectName.set("test-project")
                reportPaths.set(listOf("build/test-results/test/**/*.xml"))
            }
        """.trimIndent()
    )

    val runner = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern")

    val result = runner.buildAndFail()

    assertTrue(result.output.contains("Failed to publish test results to Fern"))
  }

  @Test
  fun `task should handle missing report files`() {
    buildFile.writeText(
      """
            plugins {
                id("com.guidewire.fern-publisher")
            }
            
            fernPublisher {
                fernUrl.set("http://localhost:${wireMockServer.port()}")
                projectName.set("test-project")
                reportPaths.set(listOf("non-existent/**/*.xml"))
            }
        """.trimIndent()
    )

    val runner = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments("publishToFern")

    val result = runner.buildAndFail()

    assertTrue(result.output.contains("No files found"))
  }
}