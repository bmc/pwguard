package controllers

import dbservice.DAO.userDAO
import models.{UserHelpers, User}
import models.UserHelpers.json._
import models.UserHelpers.json.implicits._

import play.api._
import play.api.libs.json.{JsString, Json, JsValue}
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonHelpers
import util.EitherOptionHelpers.Implicits._

import scala.concurrent.Future
import scala.util.{Success, Try}

/** Controller for reading and saving users.
  */
object UserController extends BaseController {

  override protected val logger = Logger("pwguard.controllers.UserController")

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def save(id: Int) = SecuredJSONAction {
    (currentUser: User, request: Request[JsValue]) =>

    Future {
      val res = for { userOpt <- userDAO.findByID(id).right
                      user    <- userOpt.toEither("User not found.").right
                      user2   <- decodeUserJSON(Some(user), request.body).right
                      saved   <- userDAO.save(user2).right }
                yield saved

      res match {
        case Left(error)  => Ok(jsonError(error))
        case Right(saved) => Ok(safeUserJSON(saved))
      }
    }
  }

  def create = SecuredJSONAction {
    (currentUser: User, request: Request[JsValue]) =>

    Future {
      val res = for { user  <- decodeUserJSON(None, request.body).right
                      saved <- userDAO.create(user).right }
                yield saved

      res match {
        case Left(error)  => Ok(jsonError(error))
        case Right(saved) => Ok(safeUserJSON(saved))
      }
    }
  }

  def getAll = SecuredAction { (user: User, request: Request[Any]) =>
    Future {
      if (! user.admin) {
        Forbidden("You are not an administrator")
      }

      else {
        userDAO.all match {
          case Left(error)  => Ok(jsonError(error))
          case Right(users) => {
            Ok(Json.obj("users" -> users.map { safeUserJSON(_) }))
          }
        }
      }
    }
  }

  def delete(id: Int) = SecuredAction { (user: User, request: Request[Any]) =>
    Future {
      userDAO.delete(id) match {
        case Left(error) => Ok(jsonError(error))
        case Right(_)    => Ok(Json.obj("ok" -> true))
      }
    }
  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def decodeUserJSON(userOpt: Option[User], json: JsValue):
    Either[String, User] = {

    val email     = (json \ "email").asOpt[String]
    val firstName = (json \ "firstName").asOpt[String]
    val lastName  = (json \ "lastName").asOpt[String]
    val password1 = blankToNone((json \ "password1").asOpt[String])
    val password2 = blankToNone((json \ "password2").asOpt[String])
    val admin     = (json \ "admin").asOpt[Boolean].getOrElse(false)
    val active    = (json \ "active").asOpt[Boolean].getOrElse(true)

    val pwMatch = Seq(password1, password2).flatMap {o => o} match {
      case pw1 :: pw2 :: Nil => pw1 == pw2
      case Nil               => true
      case _                 => false
    }

    if (! pwMatch) {
      Left("Passwords don't match.")
    }

    else {
      def handleExistingUser(u: User): User = {
        // Can't overwrite email address on an existing user.
        val u2 = u.copy(firstName = firstName,
                        lastName  = lastName,
                        active    = active,
                        admin     = admin)
        password1.map { pw =>
          u2.copy(encryptedPassword = UserHelpers.encryptLoginPassword(pw))
        }
       .getOrElse(u2)
      }

      def handleNewUser: Either[String, User] = {
        // New user. Email and password are required.
        val resOpt: Option[Either[String, User]] =
          for { e  <- email
                pw <- password1 }
          yield UserHelpers.createUser(email     = e,
                                       password  = pw,
                                       firstName = firstName,
                                       lastName  = lastName,
                                       admin     = admin)

        resOpt.map { res => res }.getOrElse(Left("Missing field(s)."))
      }

      userOpt.map { u => Right(handleExistingUser(u)) }
             .getOrElse { handleNewUser }
    }
  }
}
