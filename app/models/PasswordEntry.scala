package models

import java.net.URL

import _root_.util.JsonHelpers
import play.api.libs.json._
import play.api.libs.functional.syntax._

/** A password entry is owned by a user. The password and notes fields are
  * encrypted with the user's symmetric key (generated when the user is
  * created), to discourage casual perusal.
  */
trait BasePasswordEntry {
  val id:                Option[Int]
  val userID:            Int
  val name:              String
  val description:       Option[String]
  val loginID:           Option[String]
  val encryptedPassword: Option[String]
  val url:               Option[String]
  val notes:             Option[String]
}

/** A password entry record.
  *
  * @param id                 The database ID, if the record is saved
  * @param userID             The ID of the user who owns this record
  * @param name               The name associated with this entry
  * @param description        Optional freeform description
  * @param loginID            Login ID
  * @param encryptedPassword  Encrypted password
  * @param url                URL, if any
  * @param notes              Freeform notes field
  */
case class PasswordEntry(id:                Option[Int],
                         userID:            Int,
                         name:              String,
                         description:       Option[String],
                         loginID:           Option[String],
                         encryptedPassword: Option[String],
                         url:               Option[String],
                         notes:             Option[String])
  extends BasePasswordEntry with BaseModel {

  def toFullEntry(
    extras: Set[PasswordEntryExtraField] = Set.empty[PasswordEntryExtraField],
    keywords: Set[PasswordEntryKeyword]  = Set.empty[PasswordEntryKeyword]) = {
    FullPasswordEntry(id                = id,
                      userID            = userID,
                      name              = name,
                      description       = description,
                      loginID           = loginID,
                      encryptedPassword = encryptedPassword,
                      url               = url,
                      notes             = notes,
                      keywords          = keywords,
                      extraFields       = extras)
    }
}

case class FullPasswordEntry(id:                Option[Int],
                             userID:            Int,
                             name:              String,
                             description:       Option[String],
                             loginID:           Option[String],
                             encryptedPassword: Option[String],
                             url:               Option[String],
                             notes:             Option[String],
                             keywords:          Set[PasswordEntryKeyword],
                             extraFields:       Set[PasswordEntryExtraField])
  extends BasePasswordEntry with BaseModel {

  def toBaseEntry = PasswordEntry(id                = id,
                                  userID            = userID,
                                  name              = name,
                                  description       = description,
                                  loginID           = loginID,
                                  encryptedPassword = encryptedPassword,
                                  url               = url,
                                  notes             = notes)
}

object PasswordEntryHelper {
  object json {
    object implicits {

      implicit val urlWrites = new Writes[URL] {
        def writes(url: URL) = Json.toJson(url.toString)
      }

      implicit val passwordEntryWrites: Writes[PasswordEntry] = (
        (JsPath \ "id").write[Option[Int]] and
        (JsPath \ "userID").write[Int] and
        (JsPath \ "name").write[String] and
        (JsPath \ "description").write[Option[String]] and
        (JsPath \ "loginID").write[Option[String]] and
        (JsPath \ "encryptedPassword").write[Option[String]] and
        (JsPath \ "url").write[Option[String]] and
        (JsPath \ "notes").write[Option[String]]
      )(unlift(PasswordEntry.unapply))

      implicit val passwordEntryReads: Reads[PasswordEntry] = (
        (JsPath \ "id").read[Option[Int]] and
        (JsPath \ "userID").read[Int] and
        (JsPath \ "name").read[String] and
        (JsPath \ "description").read[Option[String]] and
        (JsPath \ "loginID").read[Option[String]] and
        (JsPath \ "encryptedPassword").read[Option[String]] and
        (JsPath \ "url").read[Option[String]] and
        (JsPath \ "notes").read[Option[String]]
      )(PasswordEntry.apply _)

      import PasswordEntryExtraFieldHelper.json.implicits._
      import PasswordEntryKeywordHelper.json.implicits._

      implicit val fullPasswordEntryWrites = new Writes[FullPasswordEntry] {
        def writes(o: FullPasswordEntry): JsValue = {
          JsonHelpers.addFields(Json.toJson(o.toBaseEntry),
                                ("extras" -> Json.toJson(o.extraFields.toArray)),
                                ("keywords" -> Json.toJson(o.keywords.toArray)))
        }
      }

      implicit val fullPasswordEntryReads = new Reads[FullPasswordEntry] {
        def reads(json: JsValue): JsResult[FullPasswordEntry] = {
          json.validate[PasswordEntry].flatMap { p =>
            for { extras <- (json \ "extras").validate[Array[PasswordEntryExtraField]]
                  keywords <- (json \ "keywords").validate[Array[PasswordEntryKeyword]] }
            yield p.toFullEntry(extras = extras.toSet, keywords = keywords.toSet)
          }
        }
      }
    }

  }
}
