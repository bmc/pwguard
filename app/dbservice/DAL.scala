package dbservice

import models.BaseModel

import scala.slick.driver.JdbcProfile

/** Allows dynamic selection of database type.
  */
trait Profile {
  val profile: JdbcProfile

  import profile.simple._

  abstract class ModelTable[M <: BaseModel](tag: Tag, name: String)
    extends Table[M](tag, name) {

    def id: Column[Int]
  }
}

/** Base implementation of the data access layer.
  */
class DAL(override val profile: JdbcProfile)
  extends Profile
  with    UsersComponent
  with    PasswordEntriesComponent
  with    PasswordEntryExtraFieldsComponent
  with    PasswordEntryKeywordsComponent

trait UsersComponent {
  self: Profile =>

  import profile.simple._
  import models.User

  class UsersTable(tag: Tag) extends ModelTable[User](tag, "users") {
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

  class PasswordEntriesTable(tag: Tag)
    extends ModelTable[PasswordEntry](tag, "password_entries") {

    def id                   = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userID               = column[Int]("user_id")
    def name                 = column[String]("name")
    def description          = column[Option[String]]("description")
    def loginID              = column[Option[String]]("login_id")
    def encryptedPassword    = column[Option[String]]("encrypted_password")
    def url                  = column[Option[String]]("url")
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

trait PasswordEntryExtraFieldsComponent {
  self: Profile with PasswordEntriesComponent =>

  import profile.simple._
  import models.PasswordEntryExtraField

  class PasswordEntryExtraFieldsTable(tag: Tag)
    extends ModelTable[PasswordEntryExtraField](tag, "password_entry_extra_fields") {

    def id              = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def passwordEntryID = column[Int]("password_entry_id")
    def fieldName       = column[String]("field_name")
    def fieldValue      = column[String]("field_value")

    def passwordEntry = foreignKey("pweef_id_fk", passwordEntryID, PasswordEntries)(
      _.id, onUpdate = ForeignKeyAction.Restrict
    )

    def idIndex = index("pweef_ix_id", passwordEntryID, unique=false)

    def * = (id.?, passwordEntryID.?, fieldName, fieldValue) <>
            (PasswordEntryExtraField.tupled, PasswordEntryExtraField.unapply)
  }

  val PasswordEntryExtraFields = TableQuery[PasswordEntryExtraFieldsTable]
}

trait PasswordEntryKeywordsComponent {
  self: Profile with PasswordEntriesComponent =>

  import profile.simple._
  import models.PasswordEntryKeyword

  class PasswordEntryKeywordsTable(tag: Tag)
    extends ModelTable[PasswordEntryKeyword](tag, "password_entry_keywords") {

    def id              = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def passwordEntryID = column[Int]("password_entry_id")
    def keyword         = column[String]("keyword")

    def passwordEntry = foreignKey("pwek_id_fk", passwordEntryID, PasswordEntries)(
      _.id, onUpdate = ForeignKeyAction.Restrict
    )

    def * = (id.?, passwordEntryID.?, keyword) <>
      (PasswordEntryKeyword.tupled, PasswordEntryKeyword.unapply)
  }

  val PasswordEntryKeywords = TableQuery[PasswordEntryKeywordsTable]
}
