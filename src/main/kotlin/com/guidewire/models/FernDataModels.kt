package com.guidewire.models

import kotlinx.serialization.Serializable
import java.time.ZonedDateTime

@Serializable
data class TestRun(
  var id: Long = 0,
  var testProjectName: String = "",
  var testProjectId: String = "",
  var testSeed: Long = 0,
  @Serializable(with = KZoneDateTimeSerializer::class) var startTime: ZonedDateTime? = null,
  @Serializable(with = KZoneDateTimeSerializer::class) var endTime: ZonedDateTime? = null,
  var suiteRuns: MutableList<SuiteRun> = mutableListOf()
)

@Serializable
data class SuiteRun(
  var id: Long = 0,
  var testRunId: Long = 0,
  var suiteName: String = "",
  @Serializable(with = KZoneDateTimeSerializer::class) var startTime: ZonedDateTime = ZonedDateTime.now(),
  @Serializable(with = KZoneDateTimeSerializer::class) var endTime: ZonedDateTime = ZonedDateTime.now(),
  var specRuns: MutableList<SpecRun> = mutableListOf()
)

@Serializable
data class SpecRun(
  var id: Long = 0,
  var suiteId: Long = 0,
  var specDescription: String = "",
  var status: String = "",
  var message: String = "",
  var tags: List<Tag> = emptyList(),
  @Serializable(with = KZoneDateTimeSerializer::class) var startTime: ZonedDateTime = ZonedDateTime.now(),
  @Serializable(with = KZoneDateTimeSerializer::class) var endTime: ZonedDateTime = ZonedDateTime.now()
)

@Serializable
data class Tag(
  var id: Long = 0,
  var name: String = ""
)