package com.ardentex.pwguard

import configuration.Config
import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, HttpApp}
import akka.stream.ActorMaterializer
import com.ardentex.pwguard.browscap.Browscap

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/** Main server logic. Uses the experimental `HttpApp` approach defined at
  * [[https://doc.akka.io/docs/akka-http/current/scala/http/routing-dsl/HttpApp.html]]
  *
  * @param config the loaded configuration data
  */
class WebServer(config: Config)
  extends Directives
  with JsonSupport
  with LoggingSupport
  with Util {

  implicit val system: ActorSystem = ActorSystem("pwguard")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val tBrowscap = Browscap()

  /** Start the server.
    *
    * @return `Future` on which to wait for server shutdown.
    */
  def start(): Future[Done] = {
    import config.bind

    tBrowscap match {
      case Failure(ex)     => Future.failed(ex)
      case Success(b) =>
        for { bindingFuture <- startServer(bind.host, bind.port, b)
              waitOnFuture  <- Promise[Done].future }
          yield waitOnFuture
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // --------------------------------------------------------------------------

  private def startServer(host:     String,
                          port:     Int,
                          browscap: Browscap): Future[ServerBinding] = {
    // Set 'er up.
    val bindingFuture = Http().bindAndHandle(routes(browscap), host, port)
    println(s"Server online at $host:$port. Ctrl-C to stop.")
    bindingFuture
  }

  /** The route configuration. Required by the base `HttpApp` class.
    */
  def routes(browscap: Browscap) = {
    pathSingleSlash {
      logRequest("/") {
        get {
          getFromFile(relativeToUIBase("index.html", config).toFile)
        }
      }
    } ~
    path("config") {
      headerValueByName("User-Agent") { ua =>
        complete(browscap.parseUserAgent(ua))
      }
    } ~
    pathPrefix("api" / Constants.APIVersion) {
      complete(StatusCodes.NotImplemented)
    } ~
    path(Remaining) { rest =>
      get {
        encodeResponse {
          complete(serveFile(rest, config))
        }
      }
    }
  }
}
