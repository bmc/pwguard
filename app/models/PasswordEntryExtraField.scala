package models

import play.api.libs.json.{Reads, JsPath, Writes}
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

      implicit val passwordEntryExtraFieldReads: Reads[PasswordEntryExtraField] = (
        (JsPath \ "id").read[Option[Int]] and
        (JsPath \ "passwordEntryID").read[Int] and
        (JsPath \ "fieldName").read[String] and
        (JsPath \ "fieldValue").read[String]
      )(PasswordEntryExtraField.apply _)
    }
  }
}