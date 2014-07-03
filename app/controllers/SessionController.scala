package controllers

import dbservice.DAO
import models.{UserHelper, User}
import play.api._
import play.api.libs.json.{Json, JsValue}
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current

import scala.concurrent.Future

/** Session controller.
  */
object SessionController extends BaseController {

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Handle JSON login from the web UI.
    *
    * @return Play HTTP result
    */
  def login = UnsecuredAction(BodyParsers.parse.json) {
    implicit request: Request[JsValue] =>

    Future {
      handleLogin(request) { user =>
        Ok(user.toJSON).withSession(sessionParameters(user): _*)
      }
    }
  }

  /** Get the current logged-in user, if any.
    */
  def getLoggedInUser = UnsecuredAction(BodyParsers.parse.json) {
    implicit request: Request[JsValue] =>

    Future {
      val NotLoggedIn = Json.obj("loggedIn" -> false)

      val json = currentUsername(request).map { email =>
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
  def logout = SecuredJSONAction { (user: User, request: Request[JsValue]) =>
    Future {
      val json = request.body
      val resultOpt: Option[Result] =
        for { email <- (json \ "email").asOpt[String] }
        yield {
          logger.info { s"User $email logged out."}
          Ok("").withNewSession
        }
      resultOpt.getOrElse(BadRequest)
    }
  }
  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def handleLogin(request: Request[JsValue])
                         (onSuccess: User => Result): Result = {
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
        Forbidden
      }

      case Some(Left(error)) => {
        logger.error(s"Login failure: $json: $error")
        // For some reason, Angular.js doesn't dispatch 401 responses
        // properly, so we handle them specially.
        Ok(jsonError(Some("Login failed."), Some(401)))
      }

      case Some(Right(user)) => {
        onSuccess(user)
      }
    }
  }
}
