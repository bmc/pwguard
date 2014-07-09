package controllers

import dbservice.DAO
import models.{User, PasswordEntry}
import models.PasswordEntryHelper.json.implicits._

import play.api._
import play.api.libs.json.{JsString, Json, JsValue}
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonHelpers

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

    Future {
      Ok("")
    }

  }
}
