package models

import play.api.libs.json.{Reads, JsPath, Writes}
import play.api.libs.functional.syntax._

case class PasswordEntryExtraField(id:              Option[Int],
                                   passwordEntryID: Option[Int],
                                   fieldName:       String,
                                   fieldValue:      String,
                                   isPassword:      Boolean = false)
  extends BaseModel {

  override def equals(other: Any) = {
    other match {
      case PasswordEntryExtraField(_, _, thatName, _, _) => fieldName == thatName
      case _                                             => false
    }
  }

  override val hashCode = fieldName.hashCode
}

object PasswordEntryExtraFieldHelper {
  object json {
    object implicits {
      implicit val passwordEntryExtraFieldWrites:
        Writes[PasswordEntryExtraField] = (

        (JsPath \ "id").write[Option[Int]] and
        (JsPath \ "fieldName").write[String] and
        (JsPath \ "fieldValue").write[String] and
        (JsPath \ "isPassword").write[Boolean]
      )(unlift(unapply))

      implicit val passwordEntryExtraFieldReads:
        Reads[PasswordEntryExtraField] = (

        (JsPath \ "id").read[Option[Int]] and
        (JsPath \ "fieldName").read[String] and
        (JsPath \ "fieldValue").read[String] and
        (JsPath \ "isPassword").read[Boolean]
      )(apply _)

      private def apply(id:         Option[Int],
                        name:       String,
                        value:      String,
                        isPassword: Boolean) = {
        PasswordEntryExtraField(id, None, name, value, isPassword)
      }

      private def unapply(p: PasswordEntryExtraField) = {
        Some((p.id, p.fieldName, p.fieldValue, p.isPassword))
      }
    }
  }
}
