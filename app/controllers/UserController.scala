package controllers

import dbservice.DAO.userDAO
import models.{UserHelpers, User}
import models.UserHelpers.json._
import models.UserHelpers.json.implicits._

import play.api._
import play.api.libs.json._
import play.api.mvc.{AnyContent, Action, Request}
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._

import pwguard.global.Globals.ExecutionContexts.Default._

import _root_.util.EitherOptionHelpers.Implicits._
import _root_.util.EitherOptionHelpers._

import scala.concurrent.Future
import scala.util.control.NonFatal

/** Controller for reading and saving users.
  */
object UserController extends BaseController {

  override val logger = Logger("pwguard.controllers.UserController")

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def save(id: Int) = SecuredJSONAction { authReq =>

    implicit val request = authReq.request
    val res = for { userOpt <- userDAO.findByID(id)
                    user    <- userOpt.toFuture("User not found.")
                    user2   <- decodeUserJSON(Some(user), request.body)
                    saved   <- userDAO.save(user2)
                    json    <- safeUserJSON(saved) }
              yield json

    res map { json =>
      Ok(json)
    } recover { case NonFatal(e) =>
      InternalServerError(jsonError("Save of user ID $id failed", e))
    }
  }

  def create = SecuredJSONAction { authReq =>

    implicit val request = authReq.request

    val res = for { user  <- decodeUserJSON(None, request.body)
                    saved <- userDAO.create(user)
                    json  <- safeUserJSON(saved) }
              yield json

    res map { json =>
      Ok(json)
    } recover { case NonFatal(e) =>
      InternalServerError(jsonError("Failed to create user", e))
    }
  }

  def getAll = SecuredAction { authReq =>

    if (! authReq.user.admin) {
      Future.successful(Forbidden("You are not an administrator"))
    }

    else {
      val res = for { users <- userDAO.all
                      json  <- Future.sequence(users.map { safeUserJSON _ }) }
                yield json

      res map { json =>
        Ok(Json.obj("users" -> json))
      } recover {
        case NonFatal(e) =>
          InternalServerError(jsonError("Retrieval failed", e))
      }
    }
  }

  def delete(id: Int) = SecuredJSONAction { authReq =>

    userDAO.delete(id) map { ok =>
      Ok(Json.obj("ok" -> ok))
    } recover {
      case NonFatal(e) =>
        InternalServerError(jsonError(s"Failed to delete user with ID $id", e))
    }
  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def decodeUserJSON(userOpt: Option[User], json: JsValue):
    Future[User] = {

    val email     = (json \ "email").asOpt[String]
    val firstName = (json \ "firstName").asOpt[String]
    val lastName  = (json \ "lastName").asOpt[String]
    val password1 = blankToNone((json \ "password1").asOpt[String])
    val password2 = blankToNone((json \ "password2").asOpt[String])
    val admin     = (json \ "admin").asOpt[Boolean].getOrElse(false)
    val active    = (json \ "active").asOpt[Boolean].getOrElse(true)

    val pwMatch = Seq(password1, password2).flatten match {
      case pw1 :: pw2 :: Nil => pw1 == pw2
      case Nil               => true
      case _                 => false
    }

    if (! pwMatch) {
      Future.failed(new Exception("Passwords don't match."))
    }

    else {
      def handleExistingUser(u: User): Future[User] = {
        // Can't overwrite email address on an existing user.
        val u2 = u.copy(firstName = firstName,
                        lastName  = lastName,
                        active    = active,
                        admin     = admin)
        password1.map { pw =>
          UserHelpers.encryptLoginPassword(pw) map { epw: String =>
            u2.copy(encryptedPassword = epw)
          }
        }
       .getOrElse(Future.successful(u2))
      }

      def handleNewUser: Future[User] = {
        // New user. Email and password are required.
        for { e  <- email.toFuture("Missing email field")
              pw <- password1.toFuture("Missing password1 field")
              u  <- UserHelpers.createUser(email     = e,
                                           password  = pw,
                                           firstName = firstName,
                                           lastName  = lastName,
                                           admin     = admin) }
        yield u
      }

      userOpt.map { u => handleExistingUser(u) }
             .getOrElse { handleNewUser }
    }
  }
}
