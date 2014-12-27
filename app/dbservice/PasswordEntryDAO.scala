package dbservice

import models.{PasswordEntry, User}
import play.api.Logger
import pwguard.global.Globals.ExecutionContexts.DB._
import util.RegexHelpers.Implicits._

import scala.concurrent.Future

/** DAO for interacting with User objects.
  */
class PasswordEntryDAO(_dal: DAL, _logger: Logger)
  extends BaseDAO[PasswordEntry](_dal, _logger) {

  import dal.profile.simple._
  import dal.{PasswordEntriesTable, PasswordEntries}

  private type PWEntryQuery = Query[PasswordEntriesTable, PasswordEntry, Seq]

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Get all entries for a particular user.
    *
    * @return `Right(Set[PasswordEntry])` on success, `Left(error)` on failure.
    */
  def allForUser(user: User): Future[Set[PasswordEntry]] = {
    withTransaction { implicit session =>
      user.id.map { userID =>
        val q = for { pwe <- PasswordEntries if pwe.userID === user.id }
                yield pwe
        loadMany(q)
      }.
      getOrElse(Future.successful(Set.empty[PasswordEntry]))
    }
  }

  /** Find all users with the specified IDs.

    * @param idSet the IDs
    *
    * @return `Right(Set[model])` on success, `Left(error)` on error.
    */
  def findByIDs(idSet: Set[Int]): Future[Set[PasswordEntry]] = {
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
  def findByName(user: User, name: String): Future[Option[PasswordEntry]] = {

    withTransaction { implicit session =>
      user.id.map { userID =>
        val q = for { pwe <- PasswordEntries if (pwe.userID === userID) &&
                                                (pwe.name === name) }
                yield pwe
        loadOneModel(q)
      }.
      getOrElse(Future.successful(None))
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
    Future[Set[PasswordEntry]] = {

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

      // Can't do word-only matches in the database in a database-agnostic
      // fashion. So, we need to filter here.
      loadMany(qFinal) map { matches =>
        if (fullWordOnly) {
          filterFullWordMatches(matches, lcTerm, includeDesc)
        }
        else {
          matches
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  protected def queryByID(id: Int): PWEntryQuery = {
    for {p <- PasswordEntries if p.id === id } yield p
  }

  protected def insert(pwEntry: PasswordEntry)(implicit session: SlickSession):
    Future[PasswordEntry] = {

    Future {
      val id = (PasswordEntries returning PasswordEntries.map(_.id)) += pwEntry
      pwEntry.copy(id = Some(id))
    }
  }

  protected def update(pwEntry: PasswordEntry)(implicit session: SlickSession):
    Future[PasswordEntry] = {

    Future {
      val q = for { pwe <- PasswordEntries if pwe.id === pwEntry.id.get }
              yield (pwe.userID, pwe.name, pwe.description,
                     pwe.encryptedPassword, pwe.notes)
      q.update((pwEntry.userID,
                pwEntry.name,
                pwEntry.description,
                pwEntry.encryptedPassword,
                pwEntry.notes))
      pwEntry
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def filterFullWordMatches(entries:     Set[PasswordEntry],
                                    word:        String,
                                    includeDesc: Boolean):
  Set[PasswordEntry] = {
    val re = ("""\b""" + word + """\b""").r

    entries.filter { pwe =>
      val nameMatch = re.matches(pwe.name)
      if (includeDesc) {
        pwe.description.map { re.matches(_) }.getOrElse(false) || nameMatch
      }
      else {
        nameMatch
      }
    }
  }

  private def loadMany(query: PWEntryQuery)(implicit session: SlickSession):
    Future[Set[PasswordEntry]] = {

    Future { query.sorted { _.name }.list.toSet }
  }
}
