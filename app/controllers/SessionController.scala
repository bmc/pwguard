package controllers

import dbservice.DAO
import models.{UserHelpers, User}
import models.UserHelpers.json._
import models.UserHelpers.json.implicits._

import org.joda.time.{Duration => JodaDuration}

import play.api.libs.json.{Json, JsValue}
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Logger

import pwguard.global.Globals.ExecutionContexts.Default._
import util.EitherOptionHelpers.Implicits._
import util.FutureHelpers._
import util.JsonHelpers.angularJson

import scala.concurrent.Future
import scala.util.control.NonFatal

/** Session controller. We implement our own simple session-based security.
  */
object SessionController extends BaseController {

  override val logger = Logger("pwguard.controllers.SessionController")

  // --------------------------------------------------------------------------
  // Public methods
  // --------------------------------------------------------------------------

  /** Handle JSON login from the web UI.
    *
    * @return Play HTTP result
    */
  def login = UnsecuredJSONAction { implicit request: Request[JsValue] =>

    def resultWithSession(user: User, rememberOpt: Option[JodaDuration]):
      Future[Result] = {

      val f = for { sessionData <- SessionOps.newSessionDataFor(request,
                                                                user,
                                                                rememberOpt)
                    json        <- safeUserJSON(user) }
              yield {
                val payload = Json.obj("user" -> json)
                val sessionPairs = Seq(
                  SessionOps.SessionKey -> sessionData.sessionID,
                  "email"               -> user.email
                )
                angularJson(Ok, payload) withSession (sessionPairs: _*)
              }

      f recover {
        case NonFatal(e) => {
          logger.error(s"Login failed for ${user.email}", e)
          InternalServerError
        }
      }
    }

    handleLogin(request) flatMap { case (user, rememberOpt) =>
      resultWithSession(user, rememberOpt)
    } recover {
      case NonFatal(e) => Unauthorized("Login failed")
    }
  }

  /** Get the current logged-in user, if any.
    */
  def getLoggedInUser = UnsecuredJSONAction {
    implicit request: Request[JsValue] =>

    val NotLoggedIn = Json.obj("loggedIn" -> false)

    val f = for { userOpt <- SessionOps.loggedInUser(request)
                  user    <- userOpt.toFuture("No logged in user")
                  json    <- safeUserJSON(user) }
            yield angularJson(Ok, (Json.obj("loggedIn" -> true, "user" -> json)))

    f recover {
      case NonFatal(e) => angularJson(Ok, NotLoggedIn)
    }
  }

  /** Log the current user out of the system.
    */
  def logout = SecuredJSONAction { authReq =>
    implicit val request = authReq.request
    val user = authReq.user
    val json = request.body
    SessionOps.currentSessionData(request) map { optData =>
      for (data <- optData)
        SessionOps.clearSessionData(data.sessionID)

      Ok("").withNewSession

    } recover {
      case NonFatal(e) => {
        logger.error(s"Caught exception while logging ${user.email} out", e)
        Ok("").withNewSession
      }
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // --------------------------------------------------------------------------

  /** Handles a login request.
    *
    * @param request login request
    *
    * @return A future of `(User, Option[JodaDuration])`, where the duration,
    *         if defined, represents how long to make the session last.
    */
  private def handleLogin(request: Request[JsValue]):
    Future[(User, Option[JodaDuration])] = {

    val json        = request.body
    val emailOpt    = (json \ "email").asOpt[String]
    val passwordOpt = (json \ "password").asOpt[String]
    val rememberOpt = (json \ "rememberTime").asOpt[Long]
                                             .map(JodaDuration.millis(_))

    def matchPassword(user: User): Future[Boolean] = {
      passwordOpt.map { password =>
        UserHelpers.passwordMatches(password, user.encryptedPassword)
      }.
      getOrElse(failedFuture("No password"))
    }

    if (Seq(emailOpt, passwordOpt).flatten.length != 2) {
      failedFuture("Missing email and/or password")
    }

    else {
      val email = emailOpt.get
      val f =
        for { userOpt <- DAO.userDAO.findByEmail(email)
              user    <- userOpt.toFuture(s"No such user: $email")
              matches <- matchPassword(user) if user.active }
        yield (user, rememberOpt)

      f recoverWith {
        case NonFatal(e) => {
          val msg = s"Login failure for $email"
          logger.error(msg, e)
          failedFuture(msg)
        }
      }
    }
  }
}
