package models

import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, JsPath, Writes}

case class PasswordEntrySecurityQuestion(id:              Option[Int],
                                         passwordEntryID: Option[Int],
                                         question:        String,
                                         answer:          String)
  extends BaseModel {

  override def equals(other: Any) = {
    other match {
      case PasswordEntrySecurityQuestion(_, _, q, _) => question == q
      case _                                         => false
    }
  }

  override val hashCode = question.hashCode
}


object PasswordEntrySecurityQuestionHelper {
  object json {
    object implicits {
      implicit val passwordEntrySecurityQuestionWrites:
        Writes[PasswordEntrySecurityQuestion] = (

        (JsPath \ "id").write[Option[Int]] and
        (JsPath \ "question").write[String] and
        (JsPath \ "answer").write[String]
      )(unlift(unapply))

      implicit val passwordEntrySecurityQuestionReads:
        Reads[PasswordEntrySecurityQuestion] = (

        (JsPath \ "id").read[Option[Int]] and
        (JsPath \ "question").read[String] and
        (JsPath \ "answer").read[String]
      )(apply _)

      private def apply(id:         Option[Int],
                        question:   String,
                        answer:     String) = {
        PasswordEntrySecurityQuestion(id, None, question, answer)
      }

      private def unapply(p: PasswordEntrySecurityQuestion) = {
        Some((p.id, p.question, p.answer))
      }
    }
  }
}
