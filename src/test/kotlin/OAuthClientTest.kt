import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.guidewire.oss.auth.OAuthClient
import io.github.guidewire.oss.auth.OAuthConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import org.junit.jupiter.api.extension.ExtendWith
import java.net.http.HttpRequest
import java.net.URI

@ExtendWith(SystemStubsExtension::class)

class OAuthClientTest {

  private lateinit var wireMockServer: WireMockServer
  private lateinit var tokenUrl: String

  @BeforeEach
  fun setup() {
    wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
    wireMockServer.start()
    configureFor("localhost", wireMockServer.port())
    tokenUrl = "http://localhost:${wireMockServer.port()}/oauth/token"
  }

  @AfterEach
  fun tearDown() {
    wireMockServer.stop()
  }

  @Test
  fun `fromConfig should create client with valid config`() {
    val config = OAuthConfig(
      tokenUrl = tokenUrl,
      clientId = "test-client-id",
      clientSecret = "test-client-secret",
      scopes = "fern.write"
    )

    val client = OAuthClient.fromConfig(config)

    assertNotNull(client)
  }

  @Test
  fun `fromEnvironment should return null when AUTH_URL not set`() {
    EnvironmentVariables().execute {
      val client = OAuthClient.fromEnvironment()
      assertNull(client)
    }
  }

  @Test
  fun `fromEnvironment should create client with valid environment variables`() {
    EnvironmentVariables(
      "AUTH_URL", tokenUrl,
      "FERN_AUTH_CLIENT_ID", "test-client-id",
      "FERN_AUTH_CLIENT_SECRET", "test-client-secret",
      "FERN_CLIENT_SCOPE", "fern.write"
    ).execute {
      val client = OAuthClient.fromEnvironment()
      assertNotNull(client)
    }
  }

  @Test
  fun `fromEnvironment should throw exception when AUTH_URL is set but clientId is missing`() {
    EnvironmentVariables(
      "AUTH_URL", tokenUrl,
      "FERN_AUTH_CLIENT_SECRET", "test-client-secret"
    ).execute {
      val exception = assertThrows(IllegalStateException::class.java) {
        OAuthClient.fromEnvironment()
      }
      assertTrue(exception.message?.contains("FERN_AUTH_CLIENT_ID") ?: false)
    }
  }

  @Test
  fun `fromEnvironment should throw exception when AUTH_URL is set but clientSecret is missing`() {
    EnvironmentVariables(
      "AUTH_URL", tokenUrl,
      "FERN_AUTH_CLIENT_ID", "test-client-id"
    ).execute {
      val exception = assertThrows(IllegalStateException::class.java) {
        OAuthClient.fromEnvironment()
      }
      assertTrue(exception.message?.contains("FERN_AUTH_CLIENT_SECRET") ?: false)
    }
  }

  @Test
  fun `getToken should fetch token on first call`() {
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "test-access-token-123",
                "token_type": "Bearer",
                "expires_in": 3600,
                "scope": "fern.write"
              }
              """.trimIndent()
            )
        )
    )

    val config = OAuthConfig(
      tokenUrl = tokenUrl,
      clientId = "test-client-id",
      clientSecret = "test-client-secret",
      scopes = "fern.write"
    )
    val client = OAuthClient.fromConfig(config)

    val token = client.getToken()

    assertEquals("test-access-token-123", token)
    verify(
      postRequestedFor(urlEqualTo("/oauth/token"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(containing("grant_type=client_credentials"))
        .withRequestBody(containing("client_id=test-client-id"))
        .withRequestBody(containing("client_secret=test-client-secret"))
        .withRequestBody(containing("scope=fern.write"))
    )
  }

  @Test
  fun `getToken should return cached token if not expired`() {
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "cached-token",
                "token_type": "Bearer",
                "expires_in": 3600
              }
              """.trimIndent()
            )
        )
    )

    val config = OAuthConfig(
      tokenUrl = tokenUrl,
      clientId = "test-client-id",
      clientSecret = "test-client-secret"
    )
    val client = OAuthClient.fromConfig(config)

    // First call - should fetch token
    val token1 = client.getToken()
    // Second call - should use cached token
    val token2 = client.getToken()

    assertEquals(token1, token2)
    assertEquals("cached-token", token2)
    // Verify only one call was made to the token endpoint
    verify(1, postRequestedFor(urlEqualTo("/oauth/token")))
  }

  @Test
  fun `fetchToken should throw exception on non-200 response`() {
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withBody("Invalid client credentials")
        )
    )

    val config = OAuthConfig(
      tokenUrl = tokenUrl,
      clientId = "invalid-client-id",
      clientSecret = "invalid-client-secret"
    )
    val client = OAuthClient.fromConfig(config)

    val exception = assertThrows(RuntimeException::class.java) {
      client.getToken()
    }

    assertTrue(exception.message?.contains("401") ?: false)
    assertTrue(exception.message?.contains("Failed to fetch OAuth token") ?: false)
  }

  @Test
  fun `addAuthHeader should add Bearer token to request`() {
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "bearer-token-456",
                "token_type": "Bearer",
                "expires_in": 3600
              }
              """.trimIndent()
            )
        )
    )

    val config = OAuthConfig(
      tokenUrl = tokenUrl,
      clientId = "test-client-id",
      clientSecret = "test-client-secret"
    )
    val client = OAuthClient.fromConfig(config)

    val requestBuilder = HttpRequest.newBuilder()
      .uri(URI("http://example.com/api"))
      .POST(HttpRequest.BodyPublishers.noBody())

    client.addAuthHeader(requestBuilder)
    val request = requestBuilder.build()

    val authHeader = request.headers().firstValue("Authorization").orElse(null)
    assertNotNull(authHeader)
    assertEquals("Bearer bearer-token-456", authHeader)
  }

  @Test
  fun `token request should include all required parameters`() {
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "test-token",
                "token_type": "Bearer",
                "expires_in": 3600
              }
              """.trimIndent()
            )
        )
    )

    val config = OAuthConfig(
      tokenUrl = tokenUrl,
      clientId = "my-client",
      clientSecret = "my-secret",
      scopes = "fern.write fernproject.myproject"
    )
    val client = OAuthClient.fromConfig(config)

    client.getToken()

    verify(
      postRequestedFor(urlEqualTo("/oauth/token"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(containing("grant_type=client_credentials"))
        .withRequestBody(containing("client_id=my-client"))
        .withRequestBody(containing("client_secret=my-secret"))
        .withRequestBody(containing("scope=fern.write+fernproject.myproject"))
    )
  }

  @Test
  fun `token request should not include scope parameter when scopes are empty`() {
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "test-token",
                "token_type": "Bearer",
                "expires_in": 3600
              }
              """.trimIndent()
            )
        )
    )

    val config = OAuthConfig(
      tokenUrl = tokenUrl,
      clientId = "my-client",
      clientSecret = "my-secret",
      scopes = ""
    )
    val client = OAuthClient.fromConfig(config)

    client.getToken()

    verify(
      postRequestedFor(urlEqualTo("/oauth/token"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(containing("grant_type=client_credentials"))
        .withRequestBody(containing("client_id=my-client"))
        .withRequestBody(containing("client_secret=my-secret"))
        .withRequestBody(notMatching(".*scope=.*"))
    )
  }

  @Test
  fun `fromEnvironment should handle missing scope variable gracefully`() {
    EnvironmentVariables(
      "AUTH_URL", tokenUrl,
      "FERN_AUTH_CLIENT_ID", "test-client-id",
      "FERN_AUTH_CLIENT_SECRET", "test-client-secret"
    ).execute {
      val client = OAuthClient.fromEnvironment()
      assertNotNull(client)
    }
  }

  @Test
  fun `getToken should handle token expiry correctly`() {
    var callCount = 0
    stubFor(
      post(urlEqualTo("/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "access_token": "short-lived-token-${++callCount}",
                "token_type": "Bearer",
                "expires_in": 0
              }
              """.trimIndent()
            )
        )
    )

    val config = OAuthConfig(
      tokenUrl = tokenUrl,
      clientId = "test-client-id",
      clientSecret = "test-client-secret"
    )
    val client = OAuthClient.fromConfig(config)

    // First call
    val token1 = client.getToken()
    assertNotNull(token1)

    // Wait for token to expire (0 seconds + 30 second buffer = 30 seconds)
    Thread.sleep(31000)

    // Second call should fetch a new token because the first one is expired
    val token2 = client.getToken()
    assertNotNull(token2)

     // Token should be fetched twice because expiry time has passed
    verify(2, postRequestedFor(urlEqualTo("/oauth/token")))
  }
}
