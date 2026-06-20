import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.guidewire.oss.auth.OAuthConfig
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
      post(urlEqualTo("/api/v1/test-runs"))
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
      postRequestedFor(urlEqualTo("/api/v1/test-runs"))
        .withHeader("Content-Type", equalTo("application/json"))
    )
  }

 @Test
   fun `sendTestRun should follow redirects`() {
     // Setup mock server for initial redirect
     stubFor(
       post(urlEqualTo("/api/v1/test-runs"))
         .willReturn(
           aResponse()
             .withStatus(307)
             .withHeader("Location", "/api/v1/test-runs/redirect")
         )
     )

     // Setup mock server for the redirected endpoint
     stubFor(
       post(urlEqualTo("/api/v1/test-runs/redirect"))
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
       postRequestedFor(urlEqualTo("/api/v1/test-runs"))
         .withHeader("Content-Type", equalTo("application/json"))
     )
     verify(
       postRequestedFor(urlEqualTo("/api/v1/test-runs/redirect"))
         .withHeader("Content-Type", equalTo("application/json"))
     )
   }

  @Test
  fun `sendTestRun should handle server errors`() {
    // Setup mock server to return error
    stubFor(
      post(urlEqualTo("/api/v1/test-runs"))
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
    val result = sendTestRun(testRun, "http://non-existent-server:12345", true)

    // Verify
    assertTrue(result.isFailure)
  }

  @Test
  fun `sendTestRun should include Authorization header with OAuth config`() {
    // Setup OAuth token endpoint
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "test-oauth-token-123",
                "token_type": "Bearer",
                "expires_in": 3600
              }
              """.trimIndent()
            )
        )
    )

    // Setup test data endpoint
    stubFor(
      post(urlEqualTo("/api/v1/test-runs"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("Authorization", equalTo("Bearer test-oauth-token-123"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"status\":\"success\"}")
        )
    )

    // Create OAuth config
    val oauthConfig = OAuthConfig(
      tokenUrl = "http://localhost:${wireMockServer.port()}/oauth/token",
      clientId = "test-client-id",
      clientSecret = "test-client-secret",
      scopes = "fern.write"
    )

    // Test API call with OAuth
    val result = sendTestRun(
      testRun,
      "http://localhost:${wireMockServer.port()}",
      verbose = true,
      oauthConfig = oauthConfig
    )

    // Verify success
    assertTrue(result.isSuccess)

    // Verify OAuth token was requested
    verify(
      postRequestedFor(urlEqualTo("/oauth/token"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(containing("grant_type=client_credentials"))
    )

    // Verify test run was sent with Authorization header
    verify(
      postRequestedFor(urlEqualTo("/api/v1/test-runs"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("Authorization", equalTo("Bearer test-oauth-token-123"))
    )
  }

  @Test
  fun `sendTestRun should work without authentication when OAuth not configured`() {
    // Setup mock server without OAuth requirement
    stubFor(
      post(urlEqualTo("/api/v1/test-runs"))
        .withHeader("Content-Type", equalTo("application/json"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"status\":\"success\"}")
        )
    )

    // Test API call without OAuth
    val result = sendTestRun(testRun, "http://localhost:${wireMockServer.port()}", true)

    // Verify
    assertTrue(result.isSuccess)
    verify(
      postRequestedFor(urlEqualTo("/api/v1/test-runs"))
        .withHeader("Content-Type", equalTo("application/json"))
    )

    // Verify no Authorization header was sent
    verify(
      0,
      postRequestedFor(urlEqualTo("/api/v1/test-runs"))
        .withHeader("Authorization", matching(".*"))
    )
  }

  @Test
  fun `sendTestRun should handle OAuth token fetch failures`() {
    // Setup OAuth token endpoint to fail
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withBody("Invalid client credentials")
        )
    )

    // Create OAuth config with invalid credentials
    val oauthConfig = OAuthConfig(
      tokenUrl = "http://localhost:${wireMockServer.port()}/oauth/token",
      clientId = "invalid-client-id",
      clientSecret = "invalid-client-secret",
      scopes = "fern.write"
    )

    // Test API call with invalid OAuth
    val result = sendTestRun(
      testRun,
      "http://localhost:${wireMockServer.port()}",
      verbose = true,
      oauthConfig = oauthConfig
    )

    // Verify failure
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertTrue(exception?.message?.contains("Failed to add OAuth authentication") ?: false)
  }

  @Test
  fun `sendTestRun should retry OAuth token on authentication failure`() {
    // Setup OAuth token endpoint
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "retry-token",
                "token_type": "Bearer",
                "expires_in": 3600
              }
              """.trimIndent()
            )
        )
    )

    // Setup test data endpoint to fail first, then succeed
    stubFor(
      post(urlEqualTo("/api/v1/test-runs"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("Started")
        .willReturn(
          aResponse()
            .withStatus(401)
            .withBody("{\"error\":\"Unauthorized\"}")
        )
        .willSetStateTo("First Attempt")
    )

    stubFor(
      post(urlEqualTo("/api/v1/test-runs"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("First Attempt")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"status\":\"success\"}")
        )
    )

    // Create OAuth config
    val oauthConfig = OAuthConfig(
      tokenUrl = "http://localhost:${wireMockServer.port()}/oauth/token",
      clientId = "test-client-id",
      clientSecret = "test-client-secret",
      scopes = "fern.write"
    )

    // Test API call with OAuth
    val result = sendTestRun(
      testRun,
      "http://localhost:${wireMockServer.port()}",
      verbose = true,
      oauthConfig = oauthConfig
    )

    // Verify success after retry
    assertTrue(result.isSuccess)

    // Verify multiple attempts were made (more than 1)
    verify(
      moreThan(1),
      postRequestedFor(urlEqualTo("/api/v1/test-runs"))
    )
  }

  @Test
  fun `sendTestRun should include Authorization header when following redirects`() {
    // Setup OAuth token endpoint
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "redirect-token-789",
                "token_type": "Bearer",
                "expires_in": 3600
              }
              """.trimIndent()
            )
        )
    )

    // Setup redirect
    stubFor(
      post(urlEqualTo("/api/v1/test-runs"))
        .willReturn(
          aResponse()
            .withStatus(307)
            .withHeader("Location", "/api/v1/test-runs/redirect")
        )
    )

    // Setup redirected endpoint
    stubFor(
      post(urlEqualTo("/api/v1/test-runs/redirect"))
        .withHeader("Authorization", equalTo("Bearer redirect-token-789"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"status\":\"success\"}")
        )
    )

    // Create OAuth config
    val oauthConfig = OAuthConfig(
      tokenUrl = "http://localhost:${wireMockServer.port()}/oauth/token",
      clientId = "test-client-id",
      clientSecret = "test-client-secret"
    )

    // Test API call with OAuth and redirect
    val result = sendTestRun(
      testRun,
      "http://localhost:${wireMockServer.port()}",
      verbose = true,
      oauthConfig = oauthConfig
    )

    // Verify success
    assertTrue(result.isSuccess)

    // Verify both requests included Authorization header
    verify(
      postRequestedFor(urlEqualTo("/api/v1/test-runs"))
        .withHeader("Authorization", equalTo("Bearer redirect-token-789"))
    )
    verify(
      postRequestedFor(urlEqualTo("/api/v1/test-runs/redirect"))
        .withHeader("Authorization", equalTo("Bearer redirect-token-789"))
    )
  }

  @Test
  fun `sendTestRun should use custom API endpoint path with OAuth`() {
    // Setup OAuth token endpoint
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "custom-path-token",
                "token_type": "Bearer",
                "expires_in": 3600
              }
              """.trimIndent()
            )
        )
    )

    // Setup custom endpoint
    stubFor(
      post(urlEqualTo("/custom/api/path"))
        .withHeader("Authorization", equalTo("Bearer custom-path-token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"status\":\"success\"}")
        )
    )

    // Create OAuth config
    val oauthConfig = OAuthConfig(
      tokenUrl = "http://localhost:${wireMockServer.port()}/oauth/token",
      clientId = "test-client-id",
      clientSecret = "test-client-secret"
    )

    // Test API call with OAuth and custom endpoint
    val result = sendTestRun(
      testRun,
      "http://localhost:${wireMockServer.port()}",
      verbose = true,
      oauthConfig = oauthConfig,
      apiEndpointPath = "custom/api/path"
    )

    // Verify success
    assertTrue(result.isSuccess)

    // Verify request was sent to custom endpoint with auth
    verify(
      postRequestedFor(urlEqualTo("/custom/api/path"))
        .withHeader("Authorization", equalTo("Bearer custom-path-token"))
    )
  }
}