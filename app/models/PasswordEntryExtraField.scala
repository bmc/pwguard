package models

import play.api.libs.json.{Reads, JsPath, Writes}
import play.api.libs.functional.syntax._

case class PasswordEntryExtraField(id:              Option[Int],
                                   passwordEntryID: Option[Int],
                                   fieldName:       String,
                                   fieldValue:      String)
  extends BaseModel {

  override def equals(other: Any) = {
    other match {
      case PasswordEntryExtraField(_, _, thatName, _) =>  fieldName == thatName
      case _                                          => false
    }
  }

  override val hashCode = fieldName.hashCode
}

object PasswordEntryExtraFieldHelper {
  object json {
    object implicits {
      implicit val passwordEntryExtraFieldWrites: Writes[PasswordEntryExtraField] = (
        (JsPath \ "id").write[Option[Int]] and
        (JsPath \ "fieldName").write[String] and
        (JsPath \ "fieldValue").write[String]
      )(unlift(unapply))

      implicit val passwordEntryExtraFieldReads: Reads[PasswordEntryExtraField] = (
        (JsPath \ "id").read[Option[Int]] and
        (JsPath \ "fieldName").read[String] and
        (JsPath \ "fieldValue").read[String]
      )(apply _)

      private def apply(id: Option[Int], name: String, value: String) = {
        PasswordEntryExtraField(id, None, name, value)
      }

      private def unapply(p: PasswordEntryExtraField) = {
        Some((p.id, p.fieldName, p.fieldValue))
      }
    }
  }
}
