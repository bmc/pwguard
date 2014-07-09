package dbservice

import play.api.Logger

import models.User

/** DAO for interacting with User objects.
  */
class UserDAO(_dal: DAL, _logger: Logger) extends BaseDAO[User](_dal, _logger) {

  import dal.profile.simple._
  import dal.{UsersTable, Users}

  private type UserQuery = Query[UsersTable, User]

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Get all users.
    *
    * @return `Right(Set[User])` on success, `Left(error)` on failure.
    */
  def all: Either[String, Set[User]] = {
    withTransaction { implicit session =>
      loadMany( for { u <- Users } yield u )
    }
  }

  /** Find all users with the specified IDs.

    * @param idSet the IDs
    *
    * @return `Right(Set[model])` on success, `Left(error)` on error.
    */
  def findByIDs(idSet: Set[Int]): Either[String, Set[User]] = {
    withTransaction { implicit session =>
      loadMany( for { u <- Users if u.id inSet idSet } yield u  )
    }
  }

  /** Find a user by email address.
    *
    * @param email  the email address
    *
    * @return `Left(error)` on error; `Right(None)` if no such user exists;
    *         `Right(Some(user))` if the user is found.
    */
  def findByEmail(email: String): Either[String, Option[User]] = {
    withTransaction { implicit session =>
      loadOneModel( for {u <- Users if u.email === email } yield u )
    }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  protected def queryByID(id: Int): Query[UsersTable, User] = {
    for {u <- Users if u.id === id } yield u
  }

  protected def insert(user: User)(implicit session: SlickSession):
    Either[String, User] = {

    val id = (Users returning Users.map(_.id)) += user
    Right(user.copy(id = Some(id)))
  }

  protected def update(user: User)(implicit session: SlickSession):
    Either[String, User] = {

    val q = for { u <- Users if u.id === user.id.get }
            yield (u.email, u.encryptedPassword, u.pwEntryEncryptionKey,
                   u.firstName, u.lastName, u.active, u.admin)
    q.update((user.email,
              user.encryptedPassword,
              user.pwEntryEncryptionKeyString,
              user.firstName,
              user.lastName,
              user.active,
              user.admin))
    Right(user)
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def checkUser(user: User): Either[String, User] = {
    Right(user)
  }

  private def loadMany(query: UserQuery)(implicit session: SlickSession):
    Either[String, Set[User]] = {

    Right(query.list.toSet)
  }
}
