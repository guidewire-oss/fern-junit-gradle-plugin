package io.github.guidewire.oss.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json

/**
 * OAuth configuration for client credentials flow
 */
data class OAuthConfig(
    val tokenUrl: String,
    val clientId: String,
    val clientSecret: String,
    val scopes: String = ""
)

/**
 * OAuth token response from the token endpoint
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("scope")
    val scope: String? = null
)

/**
 * OAuth client for handling client credentials flow authentication
 * Supports token caching and automatic refresh
 */
class OAuthClient private constructor(private val config: OAuthConfig) {
    private var token: TokenResponse? = null
    private var tokenExpiry: Instant = Instant.MIN

    companion object {
        /**
         * Creates a new OAuth client from environment variables
         * Returns null if OAuth is not configured (AUTH_URL not set)
         * Throws exception if AUTH_URL is set but required parameters are missing
         */
        fun fromEnvironment(): OAuthClient? {
            val tokenUrl = System.getenv("AUTH_URL") ?: return null

            // If AUTH_URL is set, validate that we have all required OAuth parameters
            val missingParams = mutableListOf<String>()
            val clientId = System.getenv("FERN_AUTH_CLIENT_ID")
            val clientSecret = System.getenv("FERN_AUTH_CLIENT_SECRET")

            if (clientId == null) {
                missingParams.add("FERN_AUTH_CLIENT_ID")
            }
            if (clientSecret == null) {
                missingParams.add("FERN_AUTH_CLIENT_SECRET")
            }

            if (missingParams.isNotEmpty()) {
                throw IllegalStateException(
                    "OAuth configuration error: AUTH_URL is set but missing required parameters: ${missingParams.joinToString(", ")}"
                )
            }

            val scopes = System.getenv("FERN_CLIENT_SCOPE") ?: ""

            return OAuthClient(
                OAuthConfig(
                    tokenUrl = tokenUrl,
                    clientId = clientId!!,
                    clientSecret = clientSecret!!,
                    scopes = scopes
                )
            )
        }

        /**
         * Creates a new OAuth client from explicit configuration
         */
        fun fromConfig(config: OAuthConfig): OAuthClient {
            return OAuthClient(config)
        }
    }

    /**
     * Gets a valid access token, fetching a new one if necessary
     */
    fun getToken(): String {
        // Check if we have a valid cached token
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token!!.accessToken
        }

        // Fetch new token
        fetchToken()
        return token!!.accessToken
    }

    /**
     * Fetches a new access token using client credentials flow
     */
    private fun fetchToken() {
        // Prepare the token request body
        val formData = buildString {
            append("grant_type=").append(urlEncode("client_credentials"))
            append("&client_id=").append(urlEncode(config.clientId))
            append("&client_secret=").append(urlEncode(config.clientSecret))
            if (config.scopes.isNotEmpty()) {
                append("&scope=").append(urlEncode(config.scopes))
            }
        }

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI(config.tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .timeout(Duration.ofSeconds(30))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw RuntimeException(
                "Failed to fetch OAuth token: HTTP ${response.statusCode()} - ${response.body()}"
            )
        }

        // Parse the token response
        val json = Json { ignoreUnknownKeys = true }
        val tokenResp = json.decodeFromString<TokenResponse>(response.body())

        // Store the token and calculate expiry
        token = tokenResp
        // Subtract 30 seconds from expiry to ensure we refresh before it actually expires
        val expiryDuration = Duration.ofSeconds((tokenResp.expiresIn - 30).coerceAtLeast(1).toLong())
        tokenExpiry = Instant.now().plus(expiryDuration)
    }

    /**
     * Adds the OAuth bearer token to the given HTTP request builder
     */
    fun addAuthHeader(requestBuilder: HttpRequest.Builder) {
        val token = getToken()
        requestBuilder.header("Authorization", "Bearer $token")
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8)
    }
}
