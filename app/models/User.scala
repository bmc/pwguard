package models

import java.security.SecureRandom
import grizzled.string.{util => GrizzledStringUtil}
import org.mindrot.jbcrypt.BCrypt
import play.api.Logger
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.Future

import _root_.util.JsonHelpers
import _root_.util.EitherOptionHelpers.Implicits._
import pwguard.global.Globals.ExecutionContexts.Default._

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
  def passwordEncryptionKey: Future[Option[Array[Byte]]] = {
    Future {
      pwEntryEncryptionKeyString.map { s: String =>
        GrizzledStringUtil.hexStringToBytes(s)
                          .map { bytes => Some(bytes) }
                          .getOrElse {
                            val msg = s"Can't decode password encryption " +
                                      s"$s for user $email"
                            Logger.error(msg)
                            throw new Exception(msg)
                          }
      }.
      getOrElse(None)
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
object UserHelpers {
  import javax.crypto.{Cipher, SecretKey}
  import javax.crypto.spec.SecretKeySpec

  private val KeyAlgorithm = "Blowfish"
  private val random       = new SecureRandom
  private val logger       = Logger("models.UserHelpers")

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
    Future[User] = {

    UserHelpers.encryptLoginPassword(password).flatMap { encryptedPassword =>
      val user              = User(id                         = None,
                                   email                      = email,
                                   encryptedPassword          = encryptedPassword,
                                   pwEntryEncryptionKeyString = None,
                                   firstName                  = firstName,
                                   lastName                   = lastName,
                                   active                     = true,
                                   admin                      = admin)

      UserHelpers.createPasswordEncryptionKey(user)
    }
  }

  /** Encrypt a user password.
    *
    * @param password plaintext password
    *
    * @return encrypted version
    */
  def encryptLoginPassword(password: String): Future[String] = {
    Future {
      BCrypt.hashpw(password, BCrypt.gensalt())
    }
  }

  /** Determine whether a plaintext password matches a previously encrypted
    * password.
    *
    * @param plaintext  the plaintext password to check
    * @param encrypted  the encrypted password to check against
    *
    * @return `true` on match, `false` on mismatch
    */
  def passwordMatches(plaintext: String, encrypted: String): Future[Boolean] = {
    Future {
      BCrypt.checkpw(plaintext, encrypted)
    }
  }

  /** Create an encryption key for a user's passwords and update the supplied
    * user object with it.
    *
    * @param user the user
    *
    * @return `Right(user)` on success; `Left(error)` on error
    */
  def createPasswordEncryptionKey(user: User): Future[User] = {
    Future {
      import javax.crypto.KeyGenerator

      val keyGenerator = KeyGenerator.getInstance(KeyAlgorithm)
      val key          = keyGenerator.generateKey()
      val keyString    = GrizzledStringUtil.bytesToHexString(key.getEncoded)

      user.copy(pwEntryEncryptionKeyString = Some(keyString))
    }.
    recover { case e: Exception =>
      logger.error(s"Can't create password encryption key for ${user.email}", e)
      throw e
    }
  }

  /** Decrypt an encrypted (stored) password for a user.
    *
    * @param user              the user
    * @param encryptedPassword the encrypted password string
    *
    * @return `Future(plaintextPassword)`
    */
  def decryptStoredPassword(user: User, encryptedPassword: String):
    Future[String] = {

    withPasswordKey(user) { key =>
      Future {
        val cipher = Cipher.getInstance(KeyAlgorithm)
        cipher.init(Cipher.DECRYPT_MODE, key)
        GrizzledStringUtil.hexStringToBytes(encryptedPassword).map { bytes =>
          val res = cipher.doFinal(bytes)
                          .map { _.asInstanceOf[Char] }
                          .mkString
          res
        }.
        getOrElse {
          val msg = s"Can't decrypt encrypted password $encryptedPassword " +
                    s"for user ${user.email}"
          logger.error(msg)
          throw new Exception(msg)
        }
      }
    }
  }

  /** Encrypt a password for storage.
    *
    * @param user              the user
    * @param plaintextPassword the plaintext password to encrypt
    *
    * @return `Right(encryptedPassword)` or `Left(error)`
    */
  def encryptStoredPassword(user: User, plaintextPassword: String):
    Future[String] = {

    withPasswordKey(user) { key =>
      Future {
        val cipher = Cipher.getInstance(KeyAlgorithm)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val result = cipher.doFinal(plaintextPassword.getBytes)
        GrizzledStringUtil.bytesToHexString(result)
      }
    }
  }

  private def withPasswordKey[T](user: User)(code: SecretKey => Future[T]):
    Future[T] = {

    def makeKey(keyBytes: Array[Byte]): Future[SecretKey] = {
      Future.successful(new SecretKeySpec(keyBytes, KeyAlgorithm))
    }

    val f = for { keyOpt   <- user.passwordEncryptionKey
                  keyBytes <- keyOpt.toFuture("Unable to decode password key")
                  key      <- makeKey(keyBytes)
                  result   <- code(key) }
            yield result

    f recoverWith { case e: Exception =>
      logger.error(s"Error while using password key for user ${user.email}", e)
      Future.failed(e)
    }
  }

  /** Various implicits, including JSON implicits.
    */
  object json {

    // Call this method to fix up the User JSON (i.e., remove sensitive
    // fields
    def safeUserJSON(user: User): Future[JsValue] = {
      import implicits._

      Future {
        JsonHelpers.addFields(
          JsonHelpers.removeFields(Json.toJson(user), UnsafeUserFields: _*),
          "displayName" -> Json.toJson(user.displayName)
        )
      }
    }

    object implicits {

      implicit val userReads: Reads[User] = (
        (JsPath \ "id").read[Option[Int]] and
        (JsPath \ "email").read[String] and
        (JsPath \ "encryptedPassword").read[String] and
        (JsPath \ "passwordEncryptionKey").read[Option[String]] and
        (JsPath \ "firstName").read[Option[String]] and
        (JsPath \ "lastName").read[Option[String]] and
        (JsPath \ "active").read[Boolean] and
        (JsPath \ "admin").read[Boolean]
      )(User.apply _)

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

  }
}
