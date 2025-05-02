package io.github.guidewire.oss.models

import org.gradle.internal.impldep.kotlinx.serialization.SerialName
import org.gradle.internal.impldep.kotlinx.serialization.Serializable

@Serializable
data class TestSuites(
  @SerialName("name")
  val name: String = "",

  @SerialName("time")
  val time: String = "",

  @SerialName("testsuite")
  val testSuites: MutableList<TestSuite> = mutableListOf()
)

@Serializable
data class TestSuite(
  @SerialName("name")
  val name: String = "",

  @SerialName("tests")
  val tests: Int = 0,

  @SerialName("skipped")
  val skipped: Int = 0,

  @SerialName("failures")
  val failures: Int = 0,

  @SerialName("errors")
  val errors: Int = 0,

  @SerialName("timestamp")
  val timestamp: String = "",

  @SerialName("time")
  val time: String = "",

  @SerialName("testcase")
  val testCases: MutableList<TestCase> = mutableListOf()
)

@Serializable
data class TestCase(
  @SerialName("name")
  val name: String = "",

  @SerialName("classname")
  val className: String = "",

  @SerialName("time")
  val time: String = "",

  @SerialName("failure")
  val failures: List<Failure> = emptyList(),

  @SerialName("error")
  val errors: List<Error> = emptyList(),

  @SerialName("skipped")
  val skips: List<Skip> = emptyList()
) {
  @Serializable
  data class Failure(
    @SerialName("message")
    val message: String = "",

    @SerialName("type")
    val type: String = "",

    @Transient
    val content: String = ""
  )

  @Serializable
  data class Error(
    @SerialName("message")
    val message: String = "",

    @SerialName("type")
    val type: String = "",

    @Transient
    val content: String = ""
  )

  @Serializable
  class Skip(
    // Empty class to represent skip tag
  ) {}
}