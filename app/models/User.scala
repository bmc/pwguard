package models

import java.security.SecureRandom
import grizzled.string.{util => GrizzledStringUtil}
import _root_.util.JsonHelpers
import org.mindrot.jbcrypt.BCrypt
import play.api.Logger
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.util.Try

/** Basic user model
  */
case class User(id:                         Option[Int],
                email:                      String,
                encryptedPassword:          String,
                pwEntryEncryptionKeyString: Option[String],
                firstName:                  Option[String],
                lastName:                   Option[String],
                active:                     Boolean,
                admin:                      Boolean)
  extends BaseModel {

  /** What to display for the user's name.
    */
  lazy val displayName = {
    Seq(firstName, lastName).flatMap(s => s) match {
      case Nil    => email
      case fields => fields mkString " "
    }
  }

  /** Get the decoded encryption key.
    *
    * @return `Right(key)` or `Left(error)`
    */
  def passwordEncryptionKey: Either[String, Option[Array[Byte]]] = {
    pwEntryEncryptionKeyString.map { s: String =>
      GrizzledStringUtil.hexStringToBytes(s)
                        .map { bytes => Right(Some(bytes)) }
                        .getOrElse(Left("Cannot decode password encryption " +
                                        s"string $pwEntryEncryptionKeyString " +
                                        s"for user $email"))
    }.
    getOrElse(Right(None))
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
  import javax.crypto.{Cipher, KeyGenerator => KG}

  private val KeyAlgorithm = "Blowfish"
  private val random       = new SecureRandom
  private val KeyGenerator = KG.getInstance(KeyAlgorithm)
  private val logger       = Logger("models.UserHelper")

  /** Helper method to create a user, with various bits already filled in.
    *
    * @param email     User's email address
    * @param password  User's password, in plaintext
    * @param firstName User's first name
    * @param lastName  User's last name
    * @param admin     Whether the user is an admin or not
    *
    * @return `Right(user)` or `Left(error)`
    */
  def createUser(email:     String,
                 password:  String,
                 firstName: Option[String] = None,
                 lastName:  Option[String] = None,
                 admin:     Boolean = false):
    Either[String, User] = {

    val encryptedPassword = UserHelper.encryptPassword(password)
    val user              = User(id                         = None,
                                 email                      = email,
                                 encryptedPassword          = encryptedPassword,
                                 pwEntryEncryptionKeyString = None,
                                 firstName                  = firstName,
                                 lastName                   = lastName,
                                 active                     = true,
                                 admin                      = admin)

    UserHelper.createPasswordEncryptionKey(user)
  }

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

  /** Create an encryption key for a user's passwords and update the supplied
    * user object with it.
    *
    * @param user the user
    *
    * @return `Right(user)` on success; `Left(error)` on error
    */
  def createPasswordEncryptionKey(user: User): Either[String, User] = {
    Try {
      val cipher = Cipher.getInstance(KeyAlgorithm)
      val key = KeyGenerator.generateKey()
      val keyString = GrizzledStringUtil.bytesToHexString(key.getEncoded)
      Right(user.copy(pwEntryEncryptionKeyString = Some(keyString)))
    }.
    recover { case e: Exception =>
      logger.error(s"Can't create password encryption key for ${user.email}", e)
      Left(e.getMessage)
    }.
    get

  }


  /** Various implicits, including JSON implicits.
    */
  object json {
    object implicits {

      implicit val userWrites: Writes[User] = (
        (JsPath \ "id").write[Option[Int]] and
        (JsPath \ "email").write[String] and
        (JsPath \ "encryptedPassword").write[String] and
        (JsPath \ "passwordEncryptionKey").write[Option[String]] and
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
