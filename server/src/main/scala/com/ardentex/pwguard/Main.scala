package com.ardentex.pwguard

import java.nio.file.Paths

import scala.util.control.NonFatal

/** Main program.
  */
object Main {

  def main(args: Array[String]): Unit = {

    if (args.length != 1) {
      System.err.println("Usage: WebServer config")
      System.exit(1)
    }

    val configPath = Paths.get(args(0))
    if (! configPath.toFile.exists) {
      System.err.println(s"""Configuration file "$configPath" doesn't exist.""")
      System.exit(1)
    }

    import configuration._

    val t = for { config <- Config.load(configPath)
                  system <- new WebServer(config).run() }
      yield system

    t.recover {
      case NonFatal(e) =>
        System.err.println("Failed to start server")
        e.printStackTrace(System.err)
        System.exit(1)
    }
  }
}
