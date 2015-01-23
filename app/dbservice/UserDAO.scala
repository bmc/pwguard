package dbservice

import play.api.Logger

import pwguard.global.Globals.ExecutionContexts.DB._

import models.User

import scala.concurrent.Future
import scala.util.Try

/** DAO for interacting with User objects.
  */
class UserDAO(_dal: DAL, _logger: Logger) extends BaseDAO[User](_dal, _logger) {

  import dal.profile.simple._
  import dal.{UsersTable, Users}

  private type UserQuery = Query[UsersTable, User, Seq]

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Get all users.
    *
    * @return `Future(Set[User])`
    */
  def all: Future[Set[User]] = {
    withTransaction { implicit session =>
      loadMany( for { u <- Users } yield u )
    }
  }

  /** Find all users with the specified IDs.

    * @param idSet the IDs
    *
    * @return `Future(Set[model])`
    */
  def findByIDs(idSet: Set[Int]): Future[Set[User]] = {
    withTransaction { implicit session =>
      loadMany( for { u <- Users if u.id inSet idSet } yield u  )
    }
  }

  /** Find a user by email address.
    *
    * @param email  the email address
    *
    * @return `Future(None)` if no such user exists;
    *         `Future(Some(user))` if the user is found.
    */
  def findByEmail(email: String): Future[Option[User]] = {
    withTransaction { implicit session =>
      loadOneModel( for {u <- Users if u.email === email } yield u )
    }
  }

  /** Create a user instance, erroring out if it already exists.
    *
    * @param user  the user object to create
    *
    * @return `Future(user)`, with a possibly changed model object
    */
  def create(user: User): Future[User] = {
    withTransaction { implicit session: SlickSession =>
      findByEmail(user.email).flatMap { userOpt =>
        userOpt.map { u => daoError(s"Email ${u.email} is taken.") }
               .getOrElse { save(user) }
      }
    }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  protected def queryByID(id: Int): UserQuery = {
    for {u <- Users if u.id === id } yield u
  }

  protected val baseQuery = Users

  protected def insert(user: User)(implicit session: SlickSession): Try[User] = {
    Try {
      val id = (Users returning Users.map(_.id)) += user
      user.copy(id = Some(id))
    }
  }

  protected def update(user: User)(implicit session: SlickSession): Try[User] = {
    Try {
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
      user
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def checkUser(user: User): Future[User] = {
    Future.successful(user)
  }

  private def loadMany(query: UserQuery)(implicit session: SlickSession):
    Future[Set[User]] = {

    Future { query.list.toSet }
  }
}
