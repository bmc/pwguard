package controllers

import dbservice.DAO
import models.User
import play.api._
import play.api.libs.json.JsValue
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current

import scala.concurrent.Future
import scala.util.{Success, Try}

/** Controller for search operations.
  */
object SearchController extends BaseController {

  override protected val logger = Logger("pwguard.controllers.SearchController")

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

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

        Ok("")
      }.
      getOrElse(BadRequest(jsonError("Missing search term")))

    }
  }
}
