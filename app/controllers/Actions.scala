package controllers

import dbservice.DAO
import models.User
import play.api.Logger
import services.Logging
import util.EitherOptionHelpers.Implicits._

import play.api.mvc._
import play.api.mvc.Results._

import pwguard.global.Globals.ExecutionContexts.Default._

import scala.concurrent.Future
import scala.util.control.NonFatal

// See https://www.playframework.com/documentation/2.3.x/ScalaActionsComposition

object LoggedAction extends ActionBuilder[Request] with Logging {

  val logger = pwguard.global.Globals.mainLogger

  def invokeBlock[T](request: Request[T],
                     block:  (Request[T]) => Future[Result]) = {
    def futureLog(msg: => String): Future[Unit] = {
      Future { logger.debug(msg) }
    }

    // Chained futures ensure that the logging occurs in the right order.
    for { f1 <- futureLog { s"Received request ${request}" }
          res <- block(request)
          f2  <- futureLog { s"Finished processing request ${request}" } }
    yield res
  }
}

object CheckSSLAction
  extends ActionBuilder[Request]
  with ActionRefiner[Request, Request] {

  def refine[T](request: Request[T]): Future[Either[Result, Request[T]]] = {
    val cfg = play.api.Play.current.configuration
    val ensureSSL = cfg.getBoolean("ensureSSL").getOrElse(false)

    def doCheck(): Future[Request[T]] = {
      Future {
        if (! ensureSSL) {
          request
        }
        else {
          val proto = request.headers.get("x-forwarded-proto")
          if (proto.isEmpty)
            throw new Exception("Can't find X-Forwarded-Proto header")
          if (! proto.get.contains("https"))
            throw new Exception("Request did not use SSL.")

          request
        }
      }
    }

    doCheck() map {
      Right(_)

    } recover {
      case NonFatal(e) => {
        val error = e.getMessage
        Logger.error(error)
        Left(PreconditionFailed(error))
      }
    }
  }
}

class AuthenticatedRequest[T](val user: User, val request: Request[T])
  extends WrappedRequest[T](request)

object AuthenticatedAction
  extends ActionBuilder[AuthenticatedRequest]
  with ActionRefiner[Request, AuthenticatedRequest] {

  private val logger = Logger("controllers.AuthenticatedAction")

  def refine[T](request: Request[T]):
    Future[Either[Result, AuthenticatedRequest[T]]] = {

    val f = for { optUser <- SessionOps.loggedInUser(request)
                  user    <- optUser.toFuture("Invalid user in session") }
            yield user

    f map { user =>
      logger.debug(s"Logged in user: ${user.email}")
      Right(new AuthenticatedRequest[T](user, request))
    } recover {
      case NonFatal(e) => {
        logger.debug(e.getMessage)
        Left(Unauthorized)
      }
    }
  }
}
