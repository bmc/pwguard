package models

import play.api.libs.json.{JsPath, Writes}
import play.api.libs.functional.syntax._

case class PasswordEntryExtraField(id:              Option[Int],
                                   passwordEntryID: Int,
                                   fieldName:       String,
                                   fieldValue:      String)
  extends BaseModel

object PasswordEntryExtraFieldHelper {
  object json {
    object implicits {
      implicit val passwordEntryExtraFieldWrites: Writes[PasswordEntryExtraField] = (
        (JsPath \ "id").write[Option[Int]] and
        (JsPath \ "passwordEntryID").write[Int] and
        (JsPath \ "fieldName").write[String] and
        (JsPath \ "fieldValue").write[String]
      )(unlift(PasswordEntryExtraField.unapply))
    }
  }
}