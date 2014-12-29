package models

import java.net.URL

import play.api.libs.json.{JsPath, Writes, Json}
import play.api.libs.functional.syntax._

/** A password entry is owned by a user. The password and notes fields are
  * encrypted with the user's symmetric key (generated when the user is
  * created), to discourage casual perusal.
  *
  */
case class PasswordEntry(id:                Option[Int],
                         userID:            Int,
                         name:              String,
                         description:       Option[String],
                         loginID:           Option[String],
                         encryptedPassword: Option[String],
                         url:               Option[String],
                         notes:             Option[String])
  extends BaseModel

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
    }
  }
}
