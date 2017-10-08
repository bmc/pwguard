package com.ardentex.pwguard

import java.io.File

import com.typesafe.config.{ConfigFactory, Config => HoconConfig}
import net.ceedubs.ficus.Ficus._
import java.nio.file.{Path, Paths}

import net.ceedubs.ficus.readers.ValueReader
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.util.Try

/** Configuration-related definitions.
  */
package object configuration {

  val DefaultPort      = 9000
  val DefaultHost      = "localhost"
  val DefaultBaseUIDir = Paths.get("../ui")

  /** HTTP binding configuration.
    *
    * @param host  the host to which to bind
    * @param port  the port to which to bind
    */
  final case class Bind(host: String, port: Int)

  /** UI configuration details.
    *
    * @param baseDir  the base directory for the UI resources.
    */
  final case class UI(baseDir: Path)

  /** Primary holder for configuration data.
    *
    * @param bind  the HTTP bind configuration data
    * @param ui    the UI configuration
    */
  final case class Config(bind: Bind, ui: UI)

  /** Companion object to Config class.
    */
  object Config {
    /** An implicit reader for use when reading java.nio.file.Path objects.
      */
    implicit val pathReader: ValueReader[Path] = new ValueReader[Path] {
      def read(config: HoconConfig, key: String): Path = {
        Paths.get(config.as[String](key))
      }
    }

    /** Load the configuration from a file.
      *
      * @param path  the file to read, as a `java.io.File`
      *
      * @return `Success(Config)` or `Failure(exception)`
      */
    def load(path: File): Try[Config] = {
      Try {
        ConfigFactory.parseFile(path)
      }
      .map { config =>
        // Use the ArbitraryTypeReader to load the case classes.
        Config(
          ui = config.as[UI]("ui"),
          bind = config.as[Bind]("bind")
        )
      }
    }

    /** Load the configuration from a file.
      *
      * @param path  the path to the file to read, as a `Path` object
      *
      * @return `Success(Config)` or `Failure(exception)`
      */
    def load(path: Path): Try[Config] = load(path.toFile)

    /** Load the configuration from a file.
      *
      * @param path  the path to the file to read, as a string
      *
      * @return `Success(Config)` or `Failure(exception)`
      */
    def load(path: String): Try[Config] = load(new File(path))
  }
}

