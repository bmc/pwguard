package controllers

import dbservice.DAO
import models.User
import services.Logging
import util.EitherOptionHelpers.Implicits._

import play.api.mvc._
import play.api.mvc.Results._

import pwguard.global.Globals.ExecutionContexts.Default._

import scala.concurrent.Future
import scala.util.control.NonFatal

class AuthenticatedRequest[T](val user: User, val request: Request[T])
  extends WrappedRequest[T](request)

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

object AuthenticatedAction
  extends ActionBuilder[AuthenticatedRequest]
  with ActionRefiner[Request, AuthenticatedRequest] {

  def refine[T](request: Request[T]):
  Future[Either[Result, AuthenticatedRequest[T]]] = {

    import DAO.userDAO

    def NoUserAction = Redirect(routes.SessionController.login())

    request.session.get("email").map { email =>
      val f = for { optUser <- userDAO.findByEmail(email)
                    user    <- optUser.toFuture("Invalid user in session") }
      yield user

      f map { user =>
        Right(new AuthenticatedRequest[T](user, request))
      } recover {
        case NonFatal(e) => Left(NoUserAction)
      }
    }.
      getOrElse(Future.successful(Left(NoUserAction)))
  }
}
