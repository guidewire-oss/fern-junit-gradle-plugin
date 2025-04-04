package io.github.guidewire.oss.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

class FernJUnitClientCommand : CliktCommand(
  name = "fern-junit-client",
) {
  override fun help(context: Context): String {
    return "CLI tool for sending JUnit test reports to Fern"
  }

  override fun run() {
    // Root command doesn't do anything on its own - just prints help message
  }
}
