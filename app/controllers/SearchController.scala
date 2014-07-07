package controllers

import dbservice.DAO
import models.{User, PasswordEntry}
import models.PasswordEntryHelper.json.implicits._

import play.api._
import play.api.libs.json.{Json, JsValue}
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import scala.util.{Success, Try}

/** Controller for search operations.
  */
object SearchController extends BaseController {

  override protected val logger = Logger("pwguard.controllers.SearchController")

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

/*
  def searchPasswordEntries() = SecuredJSONAction {
    (user: User, req: Request[JsValue]) =>
*/
  def searchPasswordEntries = SecuredJSONAction {
    (user: User, request: Request[JsValue]) =>

    Future {
      val json               = request.body
      val searchTerm         = (json \ "term").asOpt[String]
      val includeDescription = (json \ "includeDescription").asOpt[Boolean]
                                                            .getOrElse(false)
      val wordMatch          = (json \ "wordMatch").asOpt[Boolean]
                                                   .getOrElse(false)
      searchTerm.map { term =>
        DAO.passwordEntryDAO.search(user.id.getOrElse(0),
                                    term,
                                    wordMatch,
                                    includeDescription) match {
          case Left(error) => Ok(jsonError(s"Search error: $error"))
          case Right(entries) => {
            Ok(Json.obj("results" -> entries))
          }
        }
      }.
      getOrElse(BadRequest(jsonError("Missing search term")))
    }
  }
}
