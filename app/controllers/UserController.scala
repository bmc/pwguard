package controllers

import dbservice.DAO.userDAO
import models.{UserHelpers, User}

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

  def saveUser(id: Int) = SecuredJSONAction {
    (user: User, request: Request[JsValue]) =>

    import models.UserHelpers.json._

    Future {
      val json = request.body
      val firstName = (json \ "firstName").asOpt[String]
      val lastName  = (json \ "lastName").asOpt[String]
      val password1 = (json \ "password1").asOpt[String]

      val pwMatch = Seq(password1,
                        (json \ "password2").asOpt[String]).flatMap (s => s) match {

        case pw1 :: pw2 :: Nil => pw1 == pw2
        case Nil               => true
        case _                 => false
      }

      if (! pwMatch) {
        Ok(jsonError("Passwords don't match."))
      }
      else {
        def doSave(u: User): Either[String, User] = {
          val u2 = u.copy(firstName = firstName, lastName = lastName)
          val u3 = password1.map { pw =>
            u2.copy(encryptedPassword = UserHelpers.encryptLoginPassword(pw))
          }.
          getOrElse(u2)
          userDAO.save(u3)
        }

        val saveRes =
          for { userOpt   <- userDAO.findByID(id).right
                user      <- userOpt.toRight(s"User $id not found.").right
                savedUser <- doSave(user).right }
          yield savedUser

        saveRes match {
          case Left(error) => Ok(jsonError(error))
          case Right(u)    => Ok(safeUserJSON(u))
        }
      }
    }
  }
}
