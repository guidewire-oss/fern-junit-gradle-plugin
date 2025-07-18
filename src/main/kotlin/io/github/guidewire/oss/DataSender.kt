package io.github.guidewire.oss

import io.github.guidewire.oss.models.TestRun
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@OptIn(ExperimentalSerializationApi::class)
fun sendTestRun(testRun: TestRun, fernUrl: String, verbose: Boolean): Result<Unit> {
  return runCatching {
    var response: HttpResponse<String>? = null

    for (i in 0..2) {
      val json = Json {
        prettyPrint = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
      }

      val payload = json.encodeToString(testRun)
      val endpoint = "$fernUrl/api/testrun/"

      if (verbose) {
        println("Sending POST request to $endpoint...")
      }

      response = postTestRun(endpoint, fernUrl, payload)
      if (response.statusCode() < 300) {
        break
      } else {
        println("Failed to send POST request...")
        if (verbose) {
          println("Status Code: ${response.statusCode()} with body: ${response.body()}")
        }
      }
    }
    if (response?.statusCode()!! >= 300) {
      throw RuntimeException("Unexpected response code: ${response.statusCode()}")
    }
  }
}

private fun postTestRun(endpoint: String, fernUrl: String, payload: String): HttpResponse<String> {
  val client = HttpClient.newBuilder().build()
  val request = HttpRequest.newBuilder()
    .uri(URI(endpoint).normalize())
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(payload))
    .build()

  var response = client.send(request, HttpResponse.BodyHandlers.ofString())
  if (response.statusCode() == 307) {
    val locationHeader = response.headers().firstValue("location").orElseThrow { RuntimeException("Location header not found in 307 response") }
    response = postTestRun(fernUrl + locationHeader, fernUrl, payload)
  }
  return response
}