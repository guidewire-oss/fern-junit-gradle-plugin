package io.github.guidewire.oss.util

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

interface Clock {
  fun now(): ZonedDateTime
  // Add other time methods as necessary
}

class RealClock : Clock {
  override fun now(): ZonedDateTime = ZonedDateTime.now()
}

class MockClock(private val fixedTime: ZonedDateTime) : Clock {
  override fun now(): ZonedDateTime = fixedTime

  companion object {
    fun create(time: ZonedDateTime? = null): MockClock {
      return if (time != null) {
        MockClock(time)
      } else {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        val defaultTime = ZonedDateTime.parse("2006-01-02T15:04:05Z", formatter)

        MockClock(defaultTime)
      }
    }
  }
}

// Global clock instance that can be replaced for testing
object GlobalClock : Clock {
  private var instance: Clock = RealClock()

  fun setInstance(clock: Clock) {
    instance = clock
  }

  override fun now(): ZonedDateTime = instance.now()
}