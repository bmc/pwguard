package com.ardentex.pwguard

import configuration.Config
import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, HttpApp}
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/** Main server logic. Uses the experimental `HttpApp` approach defined at
  * [[https://doc.akka.io/docs/akka-http/current/scala/http/routing-dsl/HttpApp.html]]
  *
  * @param config the loaded configuration data
  */
class WebServer(config: Config)
  extends Directives
  with LoggingSupport
  with Util {

  implicit val system: ActorSystem = ActorSystem("pwguard")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  /** Run the server. Returns a `Try` of the underlying `ActorSystem`.
    *
    * @return `Success(ActorSystem)` or `Failure(exception)`
    */
  def run(): (ActorSystem, Future[Done]) = {
    val f = for { bindingFuture <- startServer(config.bind.host,
                                               config.bind.port)
                  waitOnFuture  <- hangingFuture }
      yield waitOnFuture

    (system, f)
  }

  def hangingFuture: Future[Done] = {
    val p = Promise[Done]
    p.future
  }

  def startServer(host: String, port: Int): Future[ServerBinding] = {
    // Set 'er up.
    val bindingFuture = Http().bindAndHandle(routes, host, port)
    println(s"Server online at $host:$port. Ctrl-C to stop.")
    bindingFuture
  }

  /** The route configuration. Required by the base `HttpApp` class.
    */
  def routes = {
    pathSingleSlash {
      logRequest("/") {
        get {
          log.info(s"--- ${relativeToUIBase("index.html", config).toFile}")
          getFromFile(relativeToUIBase("index.html", config).toFile)
        }
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
