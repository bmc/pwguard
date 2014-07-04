package controllers

import dbservice.DAO
import models.{UserHelper, User}
import play.api._
import play.api.libs.json.{Json, JsValue}
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current
import pwguard.global.Globals
import util.UserAgent.UserAgent

import scala.concurrent.Future

/** Session controller. We implement our own simple session-based security.
  */
object SessionController extends BaseController {

  // --------------------------------------------------------------------------
  // Public methods
  // --------------------------------------------------------------------------

  /** Handle JSON login from the web UI.
    *
    * @return Play HTTP result
    */
  def login = UnsecuredAction(BodyParsers.parse.json) {
    implicit request: Request[JsValue] =>

    def resultWithSession(user: User, userAgent: UserAgent): Result = {
      SessionOps.newSessionDataFor(request, user) match {
        case Left(error) => {
          logger.error(s"Can't store session data for ${user.email}: $error")
          InternalServerError
        }
        case Right(sessionData) => {
          val userJson = user.toJSON ++ Json.obj("isMobile" -> userAgent.isMobile)
          val payload = Json.obj("user" -> userJson)

          val sessionPairs = Seq(
            SessionOps.SessionKey  -> sessionData.sessionID
          )
          Ok(payload) withSession (sessionPairs: _*)
        }
      }
    }

    val uaService = Globals.UserAgentDecoderService
    val userAgent = request.headers.get("User-Agent").getOrElse("")
    val fLogin = handleLogin(request)
    val fUserAgent = uaService.decodeUserAgent(userAgent)

    for { userEither <- fLogin
          userAgent  <- fUserAgent }
    yield {
      userEither match {
        case Left(result) => result
        case Right(user)  => resultWithSession(user, userAgent)
      }
    }
  }

  /** Get the current logged-in user, if any.
    */
  def getLoggedInUser = UnsecuredAction(BodyParsers.parse.json) {
    implicit request: Request[JsValue] =>

    Future {
      val NotLoggedIn = Json.obj("loggedIn" -> false)

      val json = SessionOps.loggedInEmail(request).map { email =>
        DAO.userDAO.findByEmail(email) match {
          case Left(error) => {
            logger.error(s"Error loading presumably logged-in user $email")
            NotLoggedIn
          }

          case Right(None) => {
            logger.error(s"No such user: $email")
            NotLoggedIn
          }

          case Right(Some(user)) => {
            Json.obj("loggedIn" -> true, "user" -> user.toJSON)
          }
        }
      }.
      getOrElse(NotLoggedIn)
      Ok(json)
    }
  }

  /** Log the current user out of the system.
    */
  def logout = SecuredAction { (user: User, request: Request[Any]) =>
    Future {
      val json = request.body
      SessionOps.currentSessionData(request) map { data =>
        SessionOps.clearSessionData(data.sessionID)
      }

      Ok("").withNewSession
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // --------------------------------------------------------------------------

  private def handleLogin(request: Request[JsValue]):
    Future[Either[Result, User]] = {

    Future {
      val json = request.body
      val resultOpt: Option[Either[String, User]] =
        for { email    <- (json \ "email").asOpt[String]
              password <- (json \ "password").asOpt[String] }
        yield {
          DAO.userDAO.findByEmail(email).fold(
            { error => Left(error) },
            { userOpt =>

              userOpt.map { user: User =>
                if (user.active &&
                  UserHelper.passwordMatches(password, user.encryptedPassword)) {
                  Right(user)
                }
                else {
                  Left("Bad login")
                }
              }.
              getOrElse(Left("Bad login"))
            }
          )
        }

      // If any of the JSON parameters are missing, resultOpt will be None.
      // Otherwise, it'll contain the result of the lookup (which might be
      // a failure).
      resultOpt match {
        case None => {
          logger.error(s"Missing parameter(s) in JSON login request: $json")
          Left(Forbidden)
        }

        case Some(Left(error)) => {
          logger.error(s"Login failure: $json: $error")
          // For some reason, Angular.js doesn't dispatch 401 responses
          // properly, so we handle them specially.
          Left(Ok(jsonError(Some("Login failed."), Some(401))))
        }

        case Some(Right(user)) => {
          Right(user)
        }
      }
    }
  }
}
