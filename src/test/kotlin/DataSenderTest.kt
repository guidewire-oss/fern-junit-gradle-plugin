import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.guidewire.oss.models.SuiteRun
import io.github.guidewire.oss.models.TestRun
import io.github.guidewire.oss.sendTestRun
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class DataSenderTest {

  private lateinit var wireMockServer: WireMockServer
  private lateinit var testRun: TestRun

  @BeforeEach
  fun setup() {
    wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
    wireMockServer.start()
    configureFor("localhost", wireMockServer.port())

    // Create test data
    testRun = TestRun(
      testProjectName = "test-project",
      testSeed = 12345L,
      startTime = ZonedDateTime.now(),
      endTime = ZonedDateTime.now().plusMinutes(1),
      suiteRuns = mutableListOf(
        SuiteRun(
          suiteName = "Test Suite",
          startTime = ZonedDateTime.now(),
          endTime = ZonedDateTime.now().plusMinutes(1)
        )
      )
    )
  }

  @AfterEach
  fun tearDown() {
    wireMockServer.stop()
  }

  @Test
  fun `sendTestRun should successfully send data`() {
    // Setup mock server
    stubFor(
      post(urlEqualTo("/api/testrun/"))
        .withHeader("Content-Type", equalTo("application/json"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"status\":\"success\"}")
        )
    )

    // Test API call
    val result = sendTestRun(testRun, "http://localhost:${wireMockServer.port()}", true)

    // Verify
    assertTrue(result.isSuccess)
    verify(
      postRequestedFor(urlEqualTo("/api/testrun/"))
        .withHeader("Content-Type", equalTo("application/json"))
    )
  }

  @Test
  fun `sendTestRun should handle server errors`() {
    // Setup mock server to return error
    stubFor(
      post(urlEqualTo("/api/testrun/"))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withBody("{\"error\":\"Internal server error\"}")
        )
    )

    // Test API call
    val result = sendTestRun(testRun, "http://localhost:${wireMockServer.port()}", true)

    // Verify
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertTrue(exception?.message?.contains("500") ?: false)
  }

  @Test
  fun `sendTestRun should handle connection errors`() {
    // Test with non-existent server
    val result = sendTestRun(testRun, "http://non-existent-server:12345", false)

    // Verify
    assertTrue(result.isFailure)
  }
}