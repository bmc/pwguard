package models

/** Basic user model
  */
case class User(id:                   Option[Int],
                email:                String,
                encryptedPassword:    String,
                pwEntryEncryptionKey: String, // base64 encoding
                firstName:            Option[String],
                lastName:             Option[String],
                active:               Boolean)
  extends BaseModel
