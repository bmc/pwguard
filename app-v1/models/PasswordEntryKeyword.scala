package models

import play.api.libs.json.{Reads, JsPath, Writes}
import play.api.libs.functional.syntax._

case class PasswordEntryKeyword(id:              Option[Int],
                                passwordEntryID: Option[Int],
                                keyword:         String)
  extends BaseModel {

  override def equals(other: Any) = {
    other match {
      case PasswordEntryKeyword(_, _, k) => keyword == k
      case _                             => false
    }
  }

  override val hashCode = keyword.hashCode
}

object PasswordEntryKeywordHelper {
  object json {
    object implicits {
      implicit val passwordEntryKeywordWrites: Writes[PasswordEntryKeyword] = (
        (JsPath \ "id").write[Option[Int]] and
        (JsPath \ "keyword").write[String]
      )(unlift(unapply))

      implicit val passwordEntryKeywordReads: Reads[PasswordEntryKeyword] = (
        (JsPath \ "id").read[Option[Int]] and
        (JsPath \ "keyword").read[String]
      )(apply _)

      private def apply(id: Option[Int], keyword: String) = {
        PasswordEntryKeyword(id, None, keyword)
      }

      private def unapply(k: PasswordEntryKeyword) = {
        Some(k.id, k.keyword)
      }
    }
  }
}
