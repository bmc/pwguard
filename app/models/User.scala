package models

import java.security.SecureRandom
import _root_.util.JsonHelpers
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.json._
import play.api.libs.functional.syntax._

/** Basic user model
  */
case class User(id:                   Option[Int],
                email:                String,
                encryptedPassword:    String,
                pwEntryEncryptionKey: String, // base64 encoding
                firstName:            Option[String],
                lastName:             Option[String],
                active:               Boolean,
                admin:                Boolean)
  extends BaseModel {

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

/** Some helper routes for users.
  */
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


  /** Various implicits, including JSON implicits.
    */
  object json {
    object implicits {

      implicit val userWrites: Writes[User] = (
        (JsPath \ "id").write[Option[Int]] and
        (JsPath \ "email").write[String] and
        (JsPath \ "encryptedPassword").write[String] and
        (JsPath \ "passwordEncryptionKey").write[String] and
        (JsPath \ "firstName").write[Option[String]] and
        (JsPath \ "lastName").write[Option[String]] and
        (JsPath \ "active").write[Boolean] and
        (JsPath \ "admin").write[Boolean]
        )(unlift(User.unapply))
    }

    private val UnsafeUserFields = Seq("encryptedPassword",
                                       "passwordEncryptionKey")

    // Call this method to fix up the User JSON (i.e., remove sensitive
    // fields
    def safeUserJSON(user: User): JsValue = {
      import implicits._

      JsonHelpers.addFields(
        JsonHelpers.removeFields(Json.toJson(user), UnsafeUserFields: _*),
        "displayName" -> Json.toJson(user.displayName)
      )
    }
  }
}
