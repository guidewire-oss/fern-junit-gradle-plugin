package io.github.guidewire.oss

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import io.github.guidewire.oss.cli.FernJUnitClientCommand
import io.github.guidewire.oss.cli.SendCommand
import java.util.Properties
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val properties = Properties()
  val inputStream = object {}.javaClass.getResourceAsStream("/app.properties")
  inputStream?.use { properties.load(it) }

  val version = properties.getProperty("appVersion", "0.0.0")

  try {
    val command = FernJUnitClientCommand()
      .subcommands(SendCommand())
      .versionOption(version)
      .completionOption()
    command.main(args)
  } catch (e: Exception) {
    System.err.println("ERROR: [${e::class.simpleName}] ${e.message}")
    exitProcess(1)
  }
}
