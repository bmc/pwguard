package models

import play.api.libs.json.Json

/** A password entry is owned by a user. The password and notes fields are
  * encrypted with the user's symmetric key (generated when the user is
  * created), to discourage casual perusal.
  *
  */
case class PasswordEntry(id:                Option[Int],
                         userID:            Int,
                         name:              String,
                         description:       Option[String],
                         encryptedPassword: Option[String],
                         notes:             Option[String])
  extends BaseModel {

  lazy val toJSON = Json.obj(
    "id"                -> id,
    "userID"            -> userID,
    "name"              -> name,
    "description"       -> description,
    "encryptedPassword" -> encryptedPassword,
    "notes"             -> notes
  )

  def decryptPassword(key: String): Either[String, String] = {
    encryptedPassword.map { pw =>
      Left("stub")
    }.
    getOrElse(Right(""))
  }
}
