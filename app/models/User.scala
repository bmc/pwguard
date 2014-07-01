package models

import java.security.SecureRandom
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.json.Json

/** Basic user model
  */
case class User(id:                   Option[Int],
                email:                String,
                encryptedPassword:    String,
                pwEntryEncryptionKey: String, // base64 encoding
                firstName:            Option[String],
                lastName:             Option[String],
                active:               Boolean)
  extends BaseModel {

  lazy val toJSON = Json.obj(
    "id"          -> id,
    "email"       -> email,
    "firstName"   -> firstName,
    "lastName"    -> lastName,
    "displayName" -> displayName,
    "active"      -> active
  )

  /** What to display for the user's name.
    */
  lazy val displayName = {
    Seq(firstName, lastName).flatMap(s => s) match {
      case Nil    => email
      case fields => fields mkString " "
    }
  }

  /** Implementation of `equals()`, based on the email address, which must be
    * unique.
    *
    * @param other the other object
    *
    * @return `true` if equal (based on email), `false` otherwise.
    */
  override def equals(other: Any): Boolean = {
    other match {
      case that: User => this.email.equals(that.email)
      case _          => false
    }
  }

  /** Default implementation of `hashCode`, based on the email address.
    *
    * @return the hash code
    */
  override def hashCode = email.hashCode
}

object UserHelper {
  private val random = new SecureRandom

  /** Encrypt a user password.
    *
    * @param password plaintext password
    *
    * @return encrypted version
    */
  def encryptPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt())
  }

  /** Determine whether a plaintext password matches a previously encrypted
    * password.
    *
    * @param plaintext  the plaintext password to check
    * @param encrypted  the encrypted password to check against
    *
    * @return `true` on match, `false` on mismatch
    */
  def passwordMatches(plaintext: String, encrypted: String): Boolean = {
    BCrypt.checkpw(plaintext, encrypted)
  }
}
