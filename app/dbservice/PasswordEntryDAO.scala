package dbservice

import play.api.Logger

import models.{PasswordEntry, User}

/** DAO for interacting with User objects.
  */
class PasswordEntryDAO(_dal: DAL, _logger: Logger)
  extends BaseDAO[PasswordEntry](_dal, _logger) {

  import dal.profile.simple._
  import dal.{PasswordEntriesTable, PasswordEntries}

  private type PWEntryQuery = Query[PasswordEntriesTable, PasswordEntry]

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Get all entries for a particular user.
    *
    * @return `Right(Set[PasswordEntry])` on success, `Left(error)` on failure.
    */
  def allForUser(user: User): Either[String, Set[PasswordEntry]] = {
    withTransaction { implicit session =>
      user.id.map { userID =>
        val q = for { pwe <- PasswordEntries if pwe.userID === user.id }
                yield pwe
        loadMany(q)
      }.
      getOrElse(Right(Set.empty[PasswordEntry]))
    }
  }

  /** Find a user by ID.
    *
    * @param id  the user id
    *
    * @return `Left(error)` on error; `Right(None)` if no such user exists;
    *         `Right(Some(user))` if the user is found.
    */
  def findByID(id: Int): Either[String, Option[PasswordEntry]] = {
    withTransaction { implicit session =>
      val q = for { pwe <- PasswordEntries if pwe.id === id } yield pwe
      loadOneModel(q)
    }
  }

  /** Find all users with the specified IDs.

    * @param idSet the IDs
    *
    * @return `Right(Set[model])` on success, `Left(error)` on error.
    */
  def findByIDs(idSet: Set[Int]): Either[String, Set[PasswordEntry]] = {
    withTransaction { implicit session =>
      val q = for { pwe <- PasswordEntries if pwe.id inSet idSet } yield pwe
      loadMany(q)
    }
  }

  /** Find a password entry by user and entry name.
    *
    * @param user  the owner user
    * @param name  the password entry name
    *
    * @return `Left(error)` on error; `Right(None)` if no such entry exists;
    *         `Right(Some(entry))` if the entry is found.
    */
  def findByName(user: User, name: String):
    Either[String, Option[PasswordEntry]] = {

    withTransaction { implicit session =>
      user.id.map { userID =>
        val q = for { pwe <- PasswordEntries if (pwe.userID === userID) &&
                                                (pwe.name === name) }
                yield pwe
        loadOneModel(q)
      }.
      getOrElse(Right(None))
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def loadMany(query: PWEntryQuery)(implicit session: SlickSession):
    Either[String, Set[PasswordEntry]] = {

    Right(query.list.toSet)
  }
}
