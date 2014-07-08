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
      val searchTerm         = (json \ "searchTerm").asOpt[String]
      val includeDescription = (json \ "includeDescription").asOpt[Boolean]
                                                            .getOrElse(false)
      val wordMatch          = (json \ "wordMatch").asOpt[Boolean]
                                                   .getOrElse(false)

      def searchDB(term: String): Either[String, Set[PasswordEntry]] = {
        user.id.map { id =>
          DAO.passwordEntryDAO.search(id, term, wordMatch, includeDescription)
        }.
        getOrElse(Right(Set.empty[PasswordEntry]))
      }

      logger.debug(s"Received: $json")
      searchTerm.map { term =>
        val res = for { entries   <- searchDB(term).right
                        jsEntries <- jsonPasswordEntries(user, entries).right }
                  yield Json.obj("results" -> jsEntries)

        res match {
          case Left(error) => Ok(jsonError(s"Search error: $error"))
          case Right(json) => logger.error(json.toString);Ok(json)
        }
      }.
      getOrElse(BadRequest(jsonError("Missing search term")))
    }
  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  // Decrypt the encrypted passwords and produce the final JSON.
  private def jsonPasswordEntries(user:            User,
                                  passwordEntries: Set[PasswordEntry]):
    Either[String, JsValue] = {

    val mapped = passwordEntries.toList.map { pw =>
      import models.UserHelpers

      val json = Json.toJson(pw)

      pw.encryptedPassword.map { pw =>
        UserHelpers.decryptStoredPassword(user, pw).right.map { plain =>
          JsonHelpers.addFields(json, "plaintextPassword" -> JsString(plain))
        }
      }.
      getOrElse(Right(json))
    }
    mapped.filter {_.isLeft} match {
      case anything :: rest => {
        anything
      }

      case Nil => {
        // All succeeded. Everything is a Right. Yank the JSON out into a
        // JSON array.
        Right(Json.toJson(mapped.map { either => either.right.get }))
      }
    }
  }

}
