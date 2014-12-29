package dbservice

import java.net.URL

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

  private val compiledAllQuery = Compiled{ (userID: Column[Int]) =>
    val q = for { pwe <- PasswordEntries if pwe.userID === userID } yield pwe
    q.sorted(_.name)
  }

  /** Get all entries for a particular user.
    *
    * @return A future containing the results, or a failed future.
    */
  def allForUser(user: User): Future[Set[PasswordEntry]] = {
    withTransaction { implicit session =>
      user.id.map { userID =>
        Future { compiledAllQuery(userID).run.toSet }
      }.
      getOrElse(Future.successful(Set.empty[PasswordEntry]))
    }
  }

  /** Find all users with the specified IDs.

    * @param idSet the IDs
    *
    * @return A future containing the results, or a failed future.
    */
  def findByIDs(idSet: Set[Int]): Future[Set[PasswordEntry]] = {
    withTransaction { implicit session =>
      val q = for { pwe <- PasswordEntries if pwe.id inSet idSet } yield pwe
      Future { q.list.toSet }
    }
  }

  /** Find a password entry by user and entry name.
    *
    * @param user  the owner user
    * @param name  the password entry name
    *
    * @return A future containing an option of `PasswordEntry`, or a failed
    *         future on error. The option will be `None` if no matching
    *         entry was found.
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
    * @param includeDesc  whether to include the description field in the
    *                     search. Enabled by default.
    * @param includeURL   whether to include the URL field in the search.
    *                     Enabled by default.
    *
    * @return A future of the results, or a failed future.
    */
  def search(userID:       Int,
             term:         String,
             includeDesc:  Boolean = true,
             includeURL:   Boolean = true):
    Future[Set[PasswordEntry]] = {

    withTransaction { implicit session =>
      val lcTerm  = term.toLowerCase
      val sqlTerm = s"%$lcTerm%"

      val q1 = PasswordEntries.filter { _.name.toLowerCase like sqlTerm }
      val q2 = if (includeDesc) {
        q1 ++ PasswordEntries.filter { _.description.toLowerCase like sqlTerm }
      }
      else {
        q1
      }
      val q3 = if (includeURL) {
        q2 ++ PasswordEntries.filter { _.url like sqlTerm }
      }
      else {
        q2
      }

      val qFinal = q3.filter { pwe => pwe.userID === userID }

      // Can't do word-only matches in the database in a database-agnostic
      // fashion. So, we need to filter here.
      Future {
        qFinal.list.toSet
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
      val id = PasswordEntries.insert(pwEntry)
      pwEntry.copy(id = Some(id))
    }
  }

  protected def update(pwEntry: PasswordEntry)(implicit session: SlickSession):
    Future[PasswordEntry] = {

    Future {
      val q = for { pwe <- PasswordEntries if pwe.id === pwEntry.id.get }
              yield (pwe.userID, pwe.name, pwe.description,
                     pwe.encryptedPassword, pwe.notes, pwe.url)
      q.update((pwEntry.userID,
                pwEntry.name,
                pwEntry.description,
                pwEntry.encryptedPassword,
                pwEntry.notes,
                pwEntry.url))
      pwEntry
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

}
