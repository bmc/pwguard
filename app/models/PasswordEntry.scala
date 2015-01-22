package models

import java.net.URL

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

case class PasswordEntry(id:                Option[Int],
                         userID:            Int,
                         name:              String,
                         description:       Option[String],
                         loginID:           Option[String],
                         encryptedPassword: Option[String],
                         url:               Option[String],
                         notes:             Option[String])
  extends BasePasswordEntry with BaseModel {

  def toFullEntry(extras: Set[PasswordEntryExtraField] = Set.empty[PasswordEntryExtraField]) = {
    FullPasswordEntry(id, userID, name, description, loginID,
                      encryptedPassword, url, notes, extras)
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
                             extraFields:       Set[PasswordEntryExtraField])
  extends BasePasswordEntry with BaseModel {

  def toBaseEntry = PasswordEntry(id, userID, name, description, loginID,
                                  encryptedPassword, url, notes)
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

      implicit val fullPasswordEntryWrites = new Writes[FullPasswordEntry] {
        def writes(o: FullPasswordEntry): JsValue = {
          // JsValue doesn't have a "+", but JsObject does. This downcast,
          // while regrettable, is pretty much the only option.
          val j: JsObject = Json.toJson(o.toBaseEntry).asInstanceOf[JsObject]
          j + ("extras" -> Json.toJson(o.extraFields.toArray))
        }
      }

      implicit val fullPasswordEntryReads = new Reads[FullPasswordEntry] {
        def reads(json: JsValue): JsResult[FullPasswordEntry] = {
          json.validate[PasswordEntry].flatMap { p =>

            (json \ "extras").validate[Array[PasswordEntryExtraField]].map { e =>
              p.toFullEntry(e.toSet)
            }
          }
        }
      }
    }

  }
}
