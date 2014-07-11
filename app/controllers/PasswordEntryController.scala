package controllers

import dbservice.DAO
import models.{UserHelpers, User, PasswordEntry}
import models.PasswordEntryHelper.json.implicits._

import play.api._
import play.api.libs.json.{JsString, Json, JsValue}
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonHelpers
import util.EitherOptionHelpers.Implicits._

import scala.concurrent.Future
import scala.util.{Success, Try}

/** Controller for search operations.
  */
object PasswordEntryController extends BaseController {

  override protected val logger = Logger("pwguard.controllers.PasswordEntryController")

  import DAO.passwordEntryDAO

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def save(id: Int) = SecuredJSONAction {
    (user: User, request: Request[JsValue]) =>

    Future {
      val res = for { pweOpt <- passwordEntryDAO.findByID(id).right
                      pwe    <- pweOpt.toEither("Password entry not found").right
                      pwe2   <- decodeJSON(Some(pwe), user, request.body).right
                      saved  <- passwordEntryDAO.save(pwe2).right
                      json   <- jsonPasswordEntry(user, saved).right }
                yield json

      res match {
        case Left(error) => Ok(jsonError(error))
        case Right(json) => Ok(json)
      }
    }
  }

  def create = SecuredJSONAction { (user: User, request: Request[JsValue]) =>
    Future {
      val res = for { pwe   <- decodeJSON(None, user, request.body).right
                      saved <- passwordEntryDAO.save(pwe).right
                      json   <- jsonPasswordEntry(user, saved).right }
                yield json

      res match {
        case Left(error) => Ok(jsonError(error))
        case Right(json) => Ok(json)
      }
    }
  }

  def delete(id: Int) = SecuredAction { (user: User, request: Request[Any]) =>
    Future {
      passwordEntryDAO.delete(id) match {
        case Left(error) => Ok(jsonError(error))
        case Right(_)    => Ok(Json.obj("ok" -> true))
      }
    }
  }

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
          passwordEntryDAO.search(id, term, wordMatch, includeDescription)
        }.
        getOrElse(Right(Set.empty[PasswordEntry]))
      }

      searchTerm.map { term =>
        entriesToJSON(user) { searchDB(term) } match {
          case Left(error) => Ok(jsonError(s"Search failed for $user: $error"))
          case Right(json) => Ok(json)
        }
      }.
      getOrElse(BadRequest(jsonError("Missing search term")))
    }
  }

  def all = SecuredAction { (user: User, request: Request[Any]) =>
    Future {
      entriesToJSON(user) { passwordEntryDAO.allForUser(user) } match {
        case Left(error) => Ok(jsonError(s"Failed for $user: $error"))
        case Right(json) => Ok(json)
      }
    }
  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def decodeJSON(pwOpt: Option[PasswordEntry],
                         owner: User,
                         json: JsValue):
    Either[String, PasswordEntry] = {

    val nameOpt        = blankToNone((json \ "name").asOpt[String])
    val descriptionOpt = blankToNone((json \ "description").asOpt[String])
    val passwordOpt    = blankToNone((json \ "password").asOpt[String])
    val notesOpt       = blankToNone((json \ "notes").asOpt[String])
    val loginIDOpt     = blankToNone((json \ "login_id").asOpt[String])

    def maybeEncryptPassword(pwEntry: PasswordEntry):
      Either[String, PasswordEntry] = {

      passwordOpt.map { pw =>
        UserHelpers.encryptStoredPassword(owner, pw).right.map { epw =>
          pwEntry.copy(encryptedPassword = Some(epw))
        }
      }
      .getOrElse(Right(pwEntry))
    }

    def handleExisting(pw: PasswordEntry): Either[String, PasswordEntry] = {
      val pw2 = pw.copy(name        = nameOpt.getOrElse(pw.name),
                        description = descriptionOpt.orElse(pw.description),
                        notes       = notesOpt.orElse(pw.notes))
      maybeEncryptPassword(pw2)
    }

    def makeNew: Either[String, PasswordEntry] = {

      def create(name: String, userID: Int): Either[String, PasswordEntry] = {
        Right(PasswordEntry(id                = None,
                            userID            = userID,
                            name              = name,
                            description       = descriptionOpt,
                            loginID           = loginIDOpt,
                            encryptedPassword = None,
                            notes             = notesOpt))
      }

      for { name     <- nameOpt.toRight("Missing required name field").right
            userID   <- owner.id.toRight("Missing owner user ID").right
            pwEntry  <- create(name, userID).right
            pwEntry2 <- maybeEncryptPassword(pwEntry).right
            saved    <- passwordEntryDAO.save(pwEntry2).right }
      yield saved
    }

    Seq(nameOpt, descriptionOpt, passwordOpt, notesOpt).flatMap {o => o} match {
      case Nil => Left("No posted password fields.")
      case _   => pwOpt map { handleExisting(_) } getOrElse { makeNew }

    }
  }

  private def entriesToJSON(user: User)
                           (getEntries: => Either[String, Set[PasswordEntry]]):
    Either[String, JsValue] = {

    for { entries   <- getEntries.right
          jsEntries <- jsonPasswordEntries(user, entries).right }
    yield Json.obj("results" -> jsEntries)
  }

  // Decrypt the encrypted passwords and produce the final JSON.
  private def jsonPasswordEntries(user:            User,
                                  passwordEntries: Set[PasswordEntry]):
    Either[String, JsValue] = {

    val mapped = passwordEntries.toList
                                .map { jsonPasswordEntry(user, _) }

    mapped.filter { _.isLeft } match {
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

  private def jsonPasswordEntry(user: User, pwEntry: PasswordEntry):
    Either[String, JsValue] = {

    val json = Json.toJson(pwEntry)
    pwEntry.encryptedPassword.map { password =>
      UserHelpers.decryptStoredPassword(user, password).right.map { plaintext =>
        JsonHelpers.addFields(json, "plaintextPassword" -> JsString(plaintext))
      }
    }.
    getOrElse(Right(json))
  }

}
