package build

import grizzled.file.{util => fileutil}

import java.io.File

import sbt.IO

import scala.language.implicitConversions
import scala.sys.process._

/** Build helpers.
  */
package object helpers {
  /** Base directory, retrievable without needing to map over baseDirectory.
    */
  val base = fileutil.pwd

  private val reBase = s"""^${base}.""".r

  /** Log and issue a shell command.
    *
    * @param cmd  the command
    * @param log  the logger to use
    */
  def sh(cmd: String)(implicit log: sbt.Logger): Unit = {
    log.info(cmd)
    cmd.!!
  }

  /** Convert a full path to a path that's relative to the base directory.
    * Useful especially for logging.
    *
    * @param path the file
    *
    * @return the string representing the relative path
    */
  def relativePath(path: File): String = relativePath(path.toString)

  /** Convert a full path to a path that's relative to the base directory.
    * Useful especially for logging.
    *
    * @param path the file
    *
    * @return the string representing the relative path
    */
  def relativePath(path: String): String = {
    reBase.replaceFirstIn(path.toString, "")
  }

  /** Front-end to `IO.copy()` that logs what's being copied.
    *
    * @param sources  the source -> target mappings
    * @param log      the logger
    */
  def copyAll(sources: Traversable[(File, File)])
             (implicit log: sbt.Logger): Unit = {
    sources.foreach {
      case (source, target) => copy(source, target)
    }
  }

  /** Copy a source file to a target directory, logging the action.
    *
    * @param source  the source file
    * @param target  the target file
    * @param log     the logger
    */
  def copy(source: File, target: File)(implicit log: sbt.Logger): Unit = {
    log.info(s"cp ${relativePath(source)} ${relativePath(target)}")
    IO.copyFile(source, target)
  }

  /** Remove a file, logging the action.
    *
    * @param file the file to remove
    * @param log  the logger
    */
  def rm_f(file: File)(implicit log: sbt.Logger): Unit = {
    log.info(s"rm -f ${relativePath(file)}")
    file.delete()
  }

  /** Remove a file, logging the action.
    *
    * @param file the file to remove
    * @param log  the logger
    */
  def rm_f(file: String)(implicit log: sbt.Logger): Unit = rm_f(new File(file))

  /** Remove a series of files, logging the action.
    *
    * @param files the files to remove
    * @param log   the logger
    */
  def rm_f(files: Traversable[File])(implicit log: sbt.Logger): Unit = {
    val strs = files.map(relativePath(_)).mkString(" ")
    log.info(s"rm -f ${strs}")
    files.foreach(_.delete())
  }
}
