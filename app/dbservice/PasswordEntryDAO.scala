package dbservice

import models.{PasswordEntry, User}
import play.api.Logger
import util.RegexHelpers.Implicits._

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

  /** Find password entries matching a particular search term.
    *
    * @param userID       the user whose passwords are to be searched
    * @param term         the search term
    * @param fullWordOnly match only full words
    * @param includeDesc  whether to include the description field in the
    *                     search
    *
    * @return `Left(error)` on error; `Right(Set(PasswordEntry))` on
    *         success.
    */
  def search(userID:       Int,
             term:         String,
             fullWordOnly: Boolean = false,
             includeDesc:  Boolean = false):
  Either[String, Set[PasswordEntry]] = {

    withTransaction { implicit session =>
      val lcTerm  = term.toLowerCase
      val sqlTerm = s"%$lcTerm%"

      val q = if (includeDesc) {
        PasswordEntries.filter { pwe =>
          (pwe.name.toLowerCase like sqlTerm) ||
          (pwe.description.toLowerCase like sqlTerm)
        }
      }
      else {
        PasswordEntries.filter { pwe => (pwe.name.toLowerCase like sqlTerm) }
      }

      val qFinal = q.filter { pwe => pwe.userID === userID }
      logger.error(qFinal.selectStatement)

      // Can't do word-only matches in the database in a database-agnostic
      // fashion. So, we need to filter here.
      val matches = loadMany(qFinal)

      if (fullWordOnly) {
        matches.right.map { filterFullWordMatches(_, lcTerm, includeDesc) }
      }
      else {
        matches
      }
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def filterFullWordMatches(entries:     Set[PasswordEntry],
                                    word:        String,
                                    includeDesc: Boolean):
  Set[PasswordEntry] = {
    val re = """\b""" + word + """\b"""

    entries.filter { pwe =>
      val nameMatch = re.matches(pwe.name)
      if (includeDesc) {
        pwe.description.map { re.matches(_) }.getOrElse(true) || nameMatch
      }
      else {
        nameMatch
      }
    }
  }

  private def loadMany(query: PWEntryQuery)(implicit session: SlickSession):
    Either[String, Set[PasswordEntry]] = {

    Right(query.list.toSet)
  }
}
