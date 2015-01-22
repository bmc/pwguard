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

      import PasswordEntryExtraFieldHelper.json.implicits._

      implicit val fullPasswordEntryWrites: Writes[FullPasswordEntry] = new Writes[FullPasswordEntry] {
        def writes(o: FullPasswordEntry): JsValue = {
          // JsValue doesn't have a "+", but JsObject does. This downcast,
          // while regrettable, is pretty much the only option.
          val x: JsObject = Json.toJson(o.toBaseEntry).asInstanceOf[JsObject]
          x + ("extras" -> Json.toJson(o.extraFields.toArray))
        }
      }
    }

  }
}
