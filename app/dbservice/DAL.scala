package dbservice

import scala.slick.driver.JdbcProfile
import java.sql.{ ResultSet, Timestamp }

/** Allows dynamic selection of database type.
  */
trait Profile {
  val profile: JdbcProfile
}

/** Base implementation of the data access layer.
  */
class DAL(override val profile: JdbcProfile)
  extends Profile
  with    UsersComponent
  with    PasswordEntriesComponent

trait UsersComponent {
  self: Profile =>

  import profile.simple._
  import models.User

  class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def id                   = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def email                = column[String]("email")
    def encryptedPassword    = column[String]("encrypted_password")
    def pwEntryEncryptionKey = column[Option[String]]("pw_entry_encryption_key")
    def firstName            = column[Option[String]]("first_name")
    def lastName             = column[Option[String]]("last_name")
    def active               = column[Boolean]("active", O.Default(true))
    def admin                = column[Boolean]("admin", O.Default(false))

    def * = (id.?, email, encryptedPassword, pwEntryEncryptionKey,
             firstName, lastName, active, admin) <> (User.tupled, User.unapply)

    def emailIndex = index("users_ix_email", email, unique=true)
    def lnIndex    = index("users_ix_last_name", lastName)
  }

  val Users = TableQuery[UsersTable]
}

trait PasswordEntriesComponent {
  self: Profile with UsersComponent =>

  import profile.simple._
  import models.PasswordEntry
  import java.net.URL

  implicit val JavaNetURLMapper =
    MappedColumnType.base[java.net.URL, String] (
      u => u.toString,
      s => new URL(s)
    )

  class PasswordEntriesTable(tag: Tag)
    extends Table[PasswordEntry](tag, "password_entries") {

    def id                   = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userID               = column[Int]("user_id")
    def name                 = column[String]("name")
    def description          = column[Option[String]]("description")
    def loginID              = column[Option[String]]("login_id")
    def encryptedPassword    = column[Option[String]]("encrypted_password")
    def url                  = column[Option[URL]]("url")
    def notes                = column[Option[String]]("notes")

    def * = (id.?, userID, name, description, loginID, encryptedPassword,
             url, notes) <> (PasswordEntry.tupled, PasswordEntry.unapply)

    def user = foreignKey("user_fk", userID, Users)(
      _.id, onUpdate = ForeignKeyAction.Restrict
    )

    def nameIndex = index("password_entries_ix_name", (userID, name), unique=true)
  }

  val PasswordEntries = TableQuery[PasswordEntriesTable]
}
