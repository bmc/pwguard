package com.ardentex.pwguard

import configuration.Config

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.HttpApp
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/** Main server logic. Uses the experimental `HttpApp` approach defined at
  * [[https://doc.akka.io/docs/akka-http/current/scala/http/routing-dsl/HttpApp.html]]
  *
  * @param config the loaded configuration data
  */
class WebServer(config: Config) extends HttpApp with LoggingSupport with Util {

  implicit val system: ActorSystem = ActorSystem("pwguard")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  /** Run the server. Returns a `Try` of the underlying `ActorSystem`.
    *
    * @return `Success(ActorSystem)` or `Failure(exception)`
    */
  def run(): Try[ActorSystem] = {
    Try {
      startServer(config.bind.host, config.bind.port, system)
      system
    }
  }

  /** The route configuration. Required by the base `HttpApp` class.
    */
  override def routes = {
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

  /** Overridden `HttpApp.waitForShutdownSignal()` that returns a future
    * that will never be fulfilled.
    *
    * @param system  the `ActorSystem``
    * @param ec      the execution context
    *
    * @return a `Future`.
    */
  override protected def waitForShutdownSignal
    (system: ActorSystem)(implicit ec: ExecutionContext): Future[Done] = {
    val p = Promise[Done]()
    p.future
  }
}
