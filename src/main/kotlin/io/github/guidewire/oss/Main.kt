package io.github.guidewire.oss

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import io.github.guidewire.oss.cli.FernJUnitClientCommand
import io.github.guidewire.oss.cli.SendCommand
import kotlin.system.exitProcess

fun main(args: Array<String>) {

  try {
    val command = FernJUnitClientCommand()
      .subcommands(SendCommand())
      .versionOption("0.1.1") //TODO find better way of supplying version here
      .completionOption()
    command.main(args)
  } catch (e: Exception) {
    System.err.println("ERROR: ${e.message}")
    exitProcess(1)
  }
}
