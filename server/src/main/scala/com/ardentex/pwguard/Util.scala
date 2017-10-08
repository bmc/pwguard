package com.ardentex.pwguard

import java.nio.file.{AccessDeniedException, NoSuchFileException, Path, Paths}

import akka.actor.ActorSystem
import akka.event.{LogSource, LoggingAdapter}
import configuration.Config
import akka.stream.scaladsl.{Flow => AkkaFlow, Sink => AkkaSink, Source => AkkaSource}
import akka.{Done, NotUsed}
import akka.http.scaladsl.model._
import akka.stream.{Graph, SourceShape}
import akka.util.ByteString
import fs2._
import streamz.converter._
import grizzled.file.util.joinPath

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import org.apache.tika.Tika

/** A mixin with useful utility functions.
  */
trait Util {
  implicit val system: ActorSystem
  def log: akka.event.LoggingAdapter

  // For MIME type inference.
  private val tika = new Tika

  // TODO: This belongs in a resource or static file area
  val HtmlTemplate =
    """<!DOCTYPE html>
      |<html>
      |  <head>
      |    <title>$title</title>
      |    <style type="text/css">
      |      body {
      |        font-family: Helvetica, Arial, Verdana, sans-serif;
      |      }
      |    </style>
      |  </head>
      |  <body>
      |$body
      |  </body>
      |</html>""".stripMargin


  /** Generate an HTML page from a fragment of HTML, based on an HTML template.
    *
    * @param fragment   the HTML fragment, as a string
    * @param title      the document title, if any
    *
    * @return the full HTML document
    */
  def html(fragment: String, title: String = ""): String = {
    import grizzled.string.template.UnixShellStringTemplate

    def resolve(s: String): Option[String] = {
      s match {
        case "title" => Some(title)
        case "body"  => Some(fragment)
        case _       => None
      }
    }

    val t = new UnixShellStringTemplate(resolve, safe = true)
    t.sub(HtmlTemplate).getOrElse(fragment)
  }

  /** Generate an HTML page representing an error document.
    *
    * @param statusCode  the HTTP status code
    * @param msg         the human-readable message to use
    *
    * @return an HTTP response object with the appropriate status code and a    n
    *         HTML document.
    */
  def htmlError(statusCode: StatusCode, msg: String): HttpResponse = {
    // TODO: Pull fragment (template) from resources.
    val frag =
      s"""|<h1>$statusCode</h1>
          |<p>Yeah, that's an error: $msg</p>""".stripMargin

    HttpResponse(
      statusCode,
      entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, html(frag))
    )
  }

  /** Get a path that's relative to the UI base content directory.
    *
    * @param path   the relative path
    * @param config the configuration
    *
    * @return the resolved path
    */
  def relativeToUIBase(path: String, config: Config): Path = {
    Paths.get(config.ui.baseDir.toString, path.split("/"): _*)
  }

  /** Serve a static file.
    *
    * @param urlPath  the path component from the URL, which is used to locate
    *                 the file under the `UIBaseDir` directory.
    * @param config   loaded configuration
    * @param ctx      the execution context (for `Future` use)
    *
    * @return a `Future` of the `HttpResponse` that will serve the document
    *         to the browser. If the file cannot be found or cannot be read,
    *         an appropriate error `HttpResponse` is returned.
    */
  def serveFile(urlPath: String, config: Config)
               (implicit ctx: ExecutionContext): Future[HttpResponse] = {
    Future {
      val pathPieces = config.ui.baseDir.toString +: urlPath.split("/")
      val path = Paths.get(joinPath(pathPieces: _*))

      // Use Apache Tika to get the MIME type from the path. It's richer
      // and more reliable than the JDK's Files.probeContentType().
      val mime = tika.detect(path)

      // Parse the resulting string into an Akka HTTP ContentType.
      ContentType.parse(mime) match {
        case Right(ct) =>
          // Types added for clarity.
          val source: Graph[SourceShape[ByteString], NotUsed] =
            io.file
              .readAll[Task](path, 8192)
              .chunks
              .map { ch: NonEmptyChunk[Byte] => ByteString(ch.toArray) }
              .toSource
          val akkaSource: AkkaSource[ByteString, NotUsed] =
            AkkaSource.fromGraph(source)
          HttpResponse(StatusCodes.OK, entity = HttpEntity(ct, akkaSource))

        case Left(errors) =>
          log.error(s"Failed to read $path: ${errors.mkString(" ")}")
          htmlError(StatusCodes.InternalServerError, "Read failure.")
      }
    }
    .recover {
      case _: NoSuchFileException =>
        htmlError(StatusCodes.NotFound, "Not found.")
      case _: AccessDeniedException =>
        htmlError(StatusCodes.Forbidden, "Permission denied.")
      case NonFatal(e) =>
        htmlError(StatusCodes.InternalServerError, s"Read failure: $e")
    }
  }
}
