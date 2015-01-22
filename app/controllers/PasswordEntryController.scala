package controllers

import java.net.URL

import dbservice.DAO
import exceptions._
import models.{FullPasswordEntry, UserHelpers, User, PasswordEntry}
import models.PasswordEntryHelper.json.implicits._

import play.api._
import play.api.libs.json.{JsString, Json, JsValue}
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.mvc.BodyParsers._

import util.JsonHelpers
import util.EitherOptionHelpers.Implicits._
import util.EitherOptionHelpers._
import pwguard.global.Globals.ExecutionContexts.Default._

import scala.concurrent.Future
import scala.util.control.NonFatal

/** Controller for search operations.
  */
object PasswordEntryController extends BaseController {

  override val logger = Logger("pwguard.controllers.PasswordEntryController")

  import DAO.passwordEntryDAO

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def save(id: Int) = SecuredJSONAction { authReq =>

    implicit val request = authReq.request
    val user = authReq.user

    def doSave(pwe: PasswordEntry): Future[PasswordEntry] = {
      Future {
        logger.debug { s"Saving existing password entry ${pwe.name} " +
                       s"for ${user.email}" }
      } flatMap {
        case _ => passwordEntryDAO.save(pwe)
      }
    }

    val f = for { pweOpt <- passwordEntryDAO.findByID(id)
                  pwe    <- pweOpt.toFuture("Password entry not found")
                  pwe2   <- decodeJSON(Some(pwe), user, request.body)
                  saved  <- doSave(pwe2)
saved2 <- passwordEntryDAO.fullEntry(saved)
                  json   <- jsonPasswordEntry(user, saved2) }
            yield json

    f.map { json => Ok(json) }
     .recover {
      case NonFatal(e) => {
        logger.error("Save error", e)
        Ok(jsonError(e.getMessage))
      }
    }
  }

  def create = SecuredJSONAction { authReq =>

    implicit val request = authReq.request
    val user = authReq.user

    def doSave(pwe: PasswordEntry): Future[PasswordEntry] = {
      Future {
        logger.debug { s"Creating new password entry ${pwe.name} " +
                       s"for ${user.email}" }
      } flatMap {
        case _ => passwordEntryDAO.save(pwe)
      }
    }

    def checkForExisting(name: String): Future[Boolean] = {
      passwordEntryDAO.findByName(user, name) map { opt =>
        logger.error(s"$opt")
        if (opt.isDefined)
          throw new SaveFailed(s"The name " + '"' + name + '"' +
                               " is already in use.")
        true
      }
    }

    val f = for { pwe      <- decodeJSON(None, user, request.body)
                  okToSave <- checkForExisting(pwe.name) if okToSave
                  saved    <- doSave(pwe)
saved2 <- passwordEntryDAO.fullEntry(saved)
                  json     <- jsonPasswordEntry(user, saved2) }
            yield json

    f.map { json => Ok(json) }
     .recover {
      case NonFatal(e) => {
        logger.error("Create error", e)
        Ok(jsonError(e.getMessage))
      }
    }
  }

  def delete(id: Int) = SecuredAction { authReq =>
    passwordEntryDAO.delete(id) map { status =>
      Ok(Json.obj("ok" -> true))
    } recover { case NonFatal(e) =>
      Ok(jsonError(e.getMessage))
    }
  }

  def deleteMany = SecuredJSONAction { authReq =>
    Future {
      val json = authReq.request.body
      (json \ "ids").asOpt[Seq[Int]]

    } flatMap { idsOpt =>
      idsOpt map { ids =>
        passwordEntryDAO.deleteMany(authReq.user, ids.toSet) map { count =>
          Ok(Json.obj("total" -> count))
        }
      } getOrElse {
        throw new Exception("No IDs specified")
      }

    } recover {
      case NonFatal(e) => {
        logger.error(s"Unable to delete specified IDs", e)
        BadRequest(jsonError(e.getMessage))
      }
    }
  }

  def searchPasswordEntries = SecuredJSONAction { authReq =>

    implicit val request = authReq.request
    val user = authReq.user

    val json               = request.body
    val searchTerm         = (json \ "searchTerm").asOpt[String]
    val includeDescription = (json \ "includeDescription").asOpt[Boolean]
                                                          .getOrElse(false)
    val wordMatch          = (json \ "wordMatch").asOpt[Boolean]
                                                 .getOrElse(false)

    def searchDB(term: String): Future[Set[PasswordEntry]] = {
      user.id.map { id =>
        passwordEntryDAO.search(id, term, wordMatch, includeDescription)
      }.
      getOrElse(Future.successful(Set.empty[PasswordEntry]))
    }

    searchTerm.map { term =>
      entriesToJSON(user) { searchDB(term) } map { json =>
        Ok(json)
      } recover {
        case NonFatal(e) => Ok(jsonError(s"Search failed for $user:", e))
      }
    }.
    getOrElse(Future.successful(BadRequest(jsonError("Missing search term"))))
  }

  def all = SecuredAction { authReq =>

    implicit val request = authReq.request
    val user = authReq.user

    entriesToJSON(user) {
      passwordEntryDAO.allForUser(user)
    } map {
      json => Ok(json)
    } recover {
      case NonFatal(e) => Ok(jsonError(s"Failed for $user", e))
    }
  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def decodeJSON(pwOpt: Option[PasswordEntry],
                         owner: User,
                         json: JsValue):
    Future[PasswordEntry] = {

    val idOpt          = (json \ "id").asOpt[Int]
    val nameOpt        = blankToNone((json \ "name").asOpt[String])
    val descriptionOpt = blankToNone((json \ "description").asOpt[String])
    val passwordOpt    = blankToNone((json \ "password").asOpt[String])
    val notesOpt       = blankToNone((json \ "notes").asOpt[String])
    val urlOpt         = blankToNone((json \ "url").asOpt[String])
    val loginIDOpt     = blankToNone((json \ "loginID").asOpt[String])

    def maybeEncryptPassword(pwEntry: PasswordEntry): Future[PasswordEntry] = {
      passwordOpt.map { pw =>
        UserHelpers.encryptStoredPassword(owner, pw).map { epw =>
          pwEntry.copy(encryptedPassword = Some(epw))
        }
      }
      .getOrElse(Future.successful(pwEntry))
    }

    def handleExisting(pw: PasswordEntry): Future[PasswordEntry] = {

      val pw2 = pw.copy(name        = nameOpt.getOrElse(pw.name),
                        loginID     = loginIDOpt,
                        description = descriptionOpt.orElse(pw.description),
                        url         = urlOpt.orElse(pw.url),
                        notes       = notesOpt.orElse(pw.notes))
      maybeEncryptPassword(pw2)
    }

    def makeNew(): Future[PasswordEntry] = {

      def create(name: String, userID: Int): Future[PasswordEntry] = {

        Future {
          logger.debug(s"Saving new password entry ${name} for ${owner.email}")
          PasswordEntry(id                = None,
                        userID            = userID,
                        name              = name,
                        description       = descriptionOpt,
                        loginID           = loginIDOpt,
                        encryptedPassword = None,
                        url               = urlOpt,
                        notes             = notesOpt)
        }
      }

      for { name     <- nameOpt.toFuture("Missing required name field")
            userID   <- owner.id.toFuture("Missing owner user ID")
            pwEntry  <- create(name, userID)
            pwEntry2 <- maybeEncryptPassword(pwEntry) }
      yield pwEntry2
    }

    Seq(nameOpt, descriptionOpt, passwordOpt,
        notesOpt, urlOpt).flatMap {o => o} match {
      case Nil => Future.failed(new Exception("No posted password fields."))
      case _   => pwOpt map { handleExisting(_) } getOrElse { makeNew() }

    }
  }

  private def entriesToJSON(user: User)
                           (getEntries: => Future[Set[PasswordEntry]]):
    Future[JsValue] = {

    for { entries     <- getEntries
          fullEntries <- passwordEntryDAO.fullEntries(entries)
          jsEntries   <- jsonPasswordEntries(user, fullEntries) }
    yield Json.obj("results" -> jsEntries)
  }

  // Decrypt the encrypted passwords and produce the final JSON.
  private def jsonPasswordEntries(user:            User,
                                  passwordEntries: Set[FullPasswordEntry]):
    Future[JsValue] = {

    val mapped: Seq[Future[JsValue]] = passwordEntries.toSeq.map {
      jsonPasswordEntry(user, _)
    }

    // We now have a sequence of futures. Map it to a future of a sequence.
    val fSeq = Future.sequence(mapped)

    // If any future is a failure, the future-sequence will be a failure.

    fSeq.map { seq =>
      // This is a sequence of JsValue objects.
      Json.toJson(seq)
    }.
    recover {
      case NonFatal(e) =>
        Json.obj("error" -> "Unable to decrypt one or more passwords.")
    }
  }

  private def jsonPasswordEntry(user: User, pwEntry: FullPasswordEntry):
    Future[JsValue] = {

    val json = Json.toJson(pwEntry)
    pwEntry.encryptedPassword.map { password =>
      UserHelpers.decryptStoredPassword(user, password).map { plaintext =>
        JsonHelpers.addFields(json, "plaintextPassword" -> JsString(plaintext))
      }
    }.
    getOrElse {
      Future.successful(json)
    }
  }

}
