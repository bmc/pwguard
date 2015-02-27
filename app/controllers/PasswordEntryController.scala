package controllers

import dbservice.DAO
import exceptions._
import models.{FullPasswordEntry, UserHelpers, User, PasswordEntry}
import models.PasswordEntryHelper.json.implicits._

import play.api._
import play.api.libs.json._
import play.api.mvc.Results._

import _root_.util.JsonHelpers
import JsonHelpers.angularJson
import _root_.util.EitherOptionHelpers._
import _root_.util.EitherOptionHelpers.Implicits._
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

  /** Save a JSON-posted password entry.
    */
  def save(id: Int) = SecuredJSONAction { authReq =>

    implicit val request = authReq.request
    val user = authReq.user

    def doSave(pwe: FullPasswordEntry): Future[FullPasswordEntry] = {
      Future {
        logger.debug { s"Saving existing password entry ${pwe.name} " +
                       s"for ${user.email}" }
      } flatMap {
        case _ => passwordEntryDAO.saveWithDependents(pwe)
      }
    }

    val f = for { pweOpt <- passwordEntryDAO.findByID(id)
                  pwe    <- pweOpt.toFuture("Password entry not found")
                  full   <- passwordEntryDAO.fullEntry(pwe)
                  pwe2   <- decodeJSON(Some(full), user, request.body)
                  saved  <- doSave(pwe2)
                  json   <- jsonPasswordEntry(user, saved) }
            yield json

    f.map { json => angularJson(Ok, json) }
     .recover {
      case NonFatal(e) => {
        logger.error("Save error", e)
        angularJson(Ok, jsonError(e.getMessage))
      }
    }
  }

  /** Create a new JSON-posted password entry.
    */
  def create = SecuredJSONAction { authReq =>

    implicit val request = authReq.request
    val user = authReq.user

    def doSave(pwe: FullPasswordEntry): Future[FullPasswordEntry] = {
      Future {
        logger.debug { s"Creating new password entry ${pwe.name} " +
                       s"for ${user.email}" }
      } flatMap {
        case _ => passwordEntryDAO.saveWithDependents(pwe)
      }
    }

    def checkForExisting(name: String): Future[Boolean] = {
      passwordEntryDAO.findByName(user, name) map { opt =>
        if (opt.isDefined)
          throw new SaveFailed(s"The name " + '"' + name + '"' +
                               " is already in use by another password entry.")
        true
      }
    }

    val f = for { pwe      <- decodeJSON(None, user, request.body)
                  okToSave <- checkForExisting(pwe.name) if okToSave
                  saved    <- doSave(pwe)
                  json     <- jsonPasswordEntry(user, saved) }
            yield json

    f.map { json => angularJson(Ok, json) }
     .recover {
      case NonFatal(e) => {
        logger.error("Create error", e)
        angularJson(Ok, jsonError(e.getMessage))
      }
    }
  }

  def delete(id: Int) = SecuredAction { authReq =>
    passwordEntryDAO.delete(id) map { status =>
      angularJson(Ok, Json.obj("ok" -> true))
    } recover { case NonFatal(e) =>
      angularJson(Ok, jsonError(e.getMessage))
    }
  }

  def deleteMany = SecuredJSONAction { authReq =>
    Future {
      val json = authReq.request.body
      (json \ "ids").asOpt[Seq[Int]]

    } flatMap { idsOpt =>
      idsOpt map { ids =>
        passwordEntryDAO.deleteMany(authReq.user, ids.toSet) map { count =>
          angularJson(Ok, Json.obj("total" -> count))
        }
      } getOrElse {
        throw new Exception("No IDs specified")
      }

    } recover {
      case NonFatal(e) => {
        logger.error(s"Unable to delete specified IDs", e)
        angularJson(BadRequest, jsonError(e.getMessage))
      }
    }
  }

  def getEntry(id: Int) = SecuredAction { authReq =>
    val user = authReq.user

    def maybeDecryptPassword(encryptedOpt: Option[String]): Future[String] = {
      UserHelpers.decryptStoredPasswordOpt(user, encryptedOpt) map {
        _.getOrElse("")
      }
    }

    passwordEntryDAO.findByUserAndId(user, id) flatMap { opt =>
      opt map { pwe =>
        for { fpwe <- passwordEntryDAO.fullEntry(pwe)
              pw   <- maybeDecryptPassword(pwe.encryptedPassword) }
        yield {
          val js = JsonHelpers.addFields(Json.toJson(fpwe),
                                         ("password" -> Json.toJson(pw)))
          angularJson(Ok, Json.obj("passwordEntry" -> js))
        }
      } getOrElse {
        Future.successful(NotFound)
      } recover {
        case NonFatal(e) => {
          logger.error(s"Can't load entry with ID $id for user ${user.email}")
          angularJson(BadRequest, jsonError(e.getMessage))
        }
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
        angularJson(Ok, json)
      } recover {
        case NonFatal(e) => {
          angularJson(Ok, jsonError(s"Search failed for $user:", e))
        }
      }
    }.
    getOrElse(Future.successful(BadRequest(jsonError("Missing search term"))))
  }

  def getTotalForUser(userID: Int) = SecuredAction { authReq =>
    implicit val request = authReq.request
    val user = authReq.user

    import DAO.userDAO

    userDAO.findByID(userID) flatMap { userOpt =>
      userOpt map { user =>
        passwordEntryDAO.totalForUser(user).map { total =>
          angularJson(Ok, Json.obj("total" -> total))
        }
      } getOrElse {
        Future.failed(new Exception(s"No user with ID $userID"))
      }
    } recover {
      case NonFatal(e) => angularJson(Ok, jsonError(s"Failed for $user", e))
    }
  }

  def getTotal = SecuredAction { authReq =>
    implicit val request = authReq.request
    val user = authReq.user

    passwordEntryDAO.totalForUser(user).map { total =>
      angularJson(Ok, Json.obj("total" -> total))
    } recover {
      case NonFatal(e) => angularJson(Ok, jsonError(s"Failed for $user", e))
    }
  }

  def getAll = SecuredAction { authReq =>

    implicit val request = authReq.request
    val user = authReq.user

    entriesToJSON(user) {
      passwordEntryDAO.allForUser(user)
    } map {
      json => angularJson(Ok, json)
    } recover {
      case NonFatal(e) => angularJson(Ok, jsonError(s"Failed for $user", e))
    }
  }

  def getUniqueKeywords = SecuredAction { authReq =>
    implicit val request = authReq.request
    val user = authReq.user

    DAO.passwordEntryKeywordsDAO.findUniqueKeywords(user) map { keywords =>
      angularJson(Ok, Json.obj("keywords" -> keywords.toSeq))
    } recover {
      case NonFatal(e) => {
        angularJson(Ok, jsonError(s"Can't get keywords for $user", e))
      }
    }
  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def decodeJSON(pwOpt: Option[FullPasswordEntry],
                         owner: User,
                         json: JsValue):
    Future[FullPasswordEntry] = {

    import models.PasswordEntryHelper.json.implicits._

    val passwordOpt = blankToNone((json \ "password").asOpt[String])

    def objFromJSON(json: JsValue): Future[FullPasswordEntry] = {
      Future {
        json.validate[FullPasswordEntry] match {
          case pwe: JsSuccess[FullPasswordEntry] => pwe.get
          case e: JsError => throw new Exception(JsonHelpers.jsErrorToString(e))
          }
        }
    }

    def maybeEncryptPassword(pwEntry: FullPasswordEntry):
      Future[FullPasswordEntry] = {

      passwordOpt.map { pw =>
        UserHelpers.encryptStoredPassword(owner, pw).map { epw =>
          pwEntry.copy(encryptedPassword = Some(epw))
        }
      }
      .getOrElse(Future.successful(pwEntry))
    }

    def handleExisting(existing: FullPasswordEntry, newData: FullPasswordEntry):
      Future[FullPasswordEntry] = {

      val toSave = existing.copy(
        name        = newData.name,
        loginID     = newData.loginID.orElse(existing.loginID),
        description = newData.description.orElse(existing.description),
        url         = newData.url.orElse(existing.url),
        notes       = newData.notes.orElse(existing.notes),
        extraFields = newData.extraFields,
        keywords    = newData.keywords
      )

      maybeEncryptPassword(toSave)
    }

    def makeNew(pw: FullPasswordEntry): Future[FullPasswordEntry] = {

      for { userID <- owner.id.toFuture("Missing owner user ID")
            pw2    <- maybeEncryptPassword(pw) }
      yield pw2
    }

    // Augment the JSON with the owner ID. We do that here because (a) it means
    // we aren't relying on the JavaScript, and (b) it's safer.
    val js2 = JsonHelpers.addFields(json, ("userID" -> Json.toJson(owner.id.get)))

    objFromJSON(js2) flatMap { fullPwEntry =>
      pwOpt.map { existing => handleExisting(existing, fullPwEntry) }
           .getOrElse { makeNew(fullPwEntry) }
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
