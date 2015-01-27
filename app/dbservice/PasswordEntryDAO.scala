package dbservice

import models.{PasswordEntryExtraField, PasswordEntry, FullPasswordEntry, User}
import play.api.Logger
import pwguard.global.Globals.ExecutionContexts.DB._

import scala.concurrent.Future
import scala.util.{Success, Failure, Try}

/** DAO for interacting with User objects.
  */
class PasswordEntryDAO(_dal: DAL, _logger: Logger)
  extends BaseDAO[PasswordEntry](_dal, _logger) {

  override val logger = Logger("pwguard.dbservice.PasswordEntryDAO")

  import dal.profile.simple._
  import dal.{PasswordEntriesTable, PasswordEntries}

  private type PWEntryQuery = Query[PasswordEntriesTable, PasswordEntry, Seq]

  private val compiledAllQuery = Compiled{ (userID: Column[Int]) =>
    val q = for { pwe <- PasswordEntries if pwe.userID === userID } yield pwe
    q.sorted(_.name)
  }

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Get all entries for a particular user.
    *
    * @return A future containing the results, or a failed future.
    */
  def allForUser(user: User): Future[Set[PasswordEntry]] = {
    withSession { implicit session =>
      user.id.map { userID =>
        Future { compiledAllQuery(userID).run.toSet }
      }.
      getOrElse(Future.successful(Set.empty[PasswordEntry]))
    }
  }

  /** Find by (id, user) combination.
    *
    * @param user  the user
    * @param id    the ID
    *

    * @return `Future(Some(entry))` if found, `Future(None)` if not
    */
  def findByUserAndId(user: User, id: Int): Future[Option[PasswordEntry]] = {
    withTransaction { implicit session =>
      val q = for { pwe <- PasswordEntries
                    if (pwe.id === id) && (pwe.userID === user.id.get) }
              yield pwe
      loadOneModel(q)
    }
  }

  /** Find all password entries with the specified IDs.
    *
    * @param idSet the IDs
    *
    * @return A future containing the results, or a failed future.
    */
  def findByIDs(idSet: Set[Int]): Future[Set[PasswordEntry]] = {
    withSession { implicit session =>
      val q = for { pwe <- PasswordEntries if pwe.id inSet idSet } yield pwe
      Future { q.list.toSet }
    }
  }

  /** Map a set of `PasswordEntry` objects into their `FullPasswordEntry`
    * counterparts.
    *
    * @param passwordEntries the base entries
    *
    * @return a future of the mapped values
    */
  def fullEntries(passwordEntries: Set[PasswordEntry]):
    Future[Set[FullPasswordEntry]] = {

    withSession { implicit session =>
      loadDependents(passwordEntries)
    }
  }

  /** Map a `PasswordEntry` object into its `FullPasswordEntry` counterpart.
    *
    * @param passwordEntry the base entry
    *
    * @return a future of the mapped entry
    */
  def fullEntry(passwordEntry: PasswordEntry): Future[FullPasswordEntry] = {
    withSession { implicit session =>
      loadDependents(passwordEntry)
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

  /** Delete many entries.
    *
    * @param user the user who owns the password entries
    * @param ids  the IDs. Any IDs not belonging to the user are ignored.
    *
    * @return A future containing the actual number deleted
    */
  def deleteMany(user: User, ids: Set[Int]): Future[Int] = {
    withTransaction { implicit session =>
      val qIDs = for { pw <- PasswordEntries if (pw.id inSet ids) &&
                                                (pw.userID === user.id) }
                 yield pw.id

      val actualIDs: Set[Int] = qIDs.list.toSet
      val removed = ids -- actualIDs
      if (removed.size > 0) {
        val sIDs = removed.map {_.toString}.mkString(",")
        logger.error(s"User ${user.email} attempted to delete password " +
                     s"entries ${sIDs}, but they belong to someone else.")
      }

      if (actualIDs.isEmpty) {
        logger.error(s"No legitimate IDs to be deleted.")
        Future.successful(0)
      }
      else {
        val qDel = for { pw <- PasswordEntries if pw.id inSet ids } yield pw
        Future { qDel.delete }
      }
    }
  }

  /** Save a full password entry, with all its dependents.
    *
    * @param entryToSave  the full password entry
    *
    * @return A future of the saved entry, which may be modified
    */
  def saveWithDependents(entryToSave: FullPasswordEntry):
    Future[FullPasswordEntry] = {

    withTransaction { implicit session =>
      val baseEntry = entryToSave.toBaseEntry
      val extrasDAO = DAO.passwordEntryExtraFieldsDAO

      def handleExtraFields(savedEntry: PasswordEntry,
                            extras:     Set[PasswordEntryExtraField])
                           (implicit session: SlickSession) = {

        // This logic isn't 100% correct. A change in a field name will
        // cause it to appear to be new, because the name is the only hash
        // code. That's not optimal, but it isn't worth optimizing right now.
        // The simplest solution, the one that has the least possibility of
        // going wrong, is just to delete all the old entries and (re-)insert
        // the new ones.
        extrasDAO.deleteForPasswordEntry(savedEntry) flatMap { n =>
          // Make sure we associate the extras with this password entry.
          val adjustedExtras = extras.map {
            _.copy(id = None, passwordEntryID = savedEntry.id)
          }

          extrasDAO.saveMany(adjustedExtras) map { savedExtras =>
            savedEntry.toFullEntry(savedExtras)
          }
        }
      }

      for { savedBaseEntry  <- save(baseEntry)
            fullySavedEntry <- handleExtraFields(savedBaseEntry,
                                                 entryToSave.extraFields) }
      yield fullySavedEntry
    }
  }

  override def delete(id: Int): Future[Boolean] = {
    withTransaction { implicit session =>
      DAO.passwordEntryExtraFieldsDAO.deleteForPasswordEntry(id) flatMap { n =>
        logger.debug(s"Deleted $n password entry extra fields.");
        super.deleteInSession(id)
      }
    }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  protected def queryByID(id: Int): PWEntryQuery = {
    for (p <- PasswordEntries if p.id === id) yield p
  }

  protected val baseQuery = PasswordEntries

  protected def insert(pwEntry: PasswordEntry)(implicit session: SlickSession):
    Try[PasswordEntry] = {

    doInsert(pwEntry) map { id => pwEntry.copy(id = Some(id)) }
  }

  protected def update(pwEntry: PasswordEntry)(implicit session: SlickSession):
    Try[PasswordEntry] = {

    Try {
      val q = for { pwe <- PasswordEntries if pwe.id === pwEntry.id.get }
              yield (pwe.userID, pwe.name, pwe.description, pwe.loginID,
                     pwe.encryptedPassword, pwe.notes, pwe.url)
      q.update((pwEntry.userID,
                pwEntry.name,
                pwEntry.description,
                pwEntry.loginID,
                pwEntry.encryptedPassword,
                pwEntry.notes,
                pwEntry.url))
      pwEntry
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def loadDependents(pw: PasswordEntry)
                            (implicit session: SlickSession):
    Future[FullPasswordEntry] = {

    DAO.passwordEntryExtraFieldsDAO.findForPasswordEntry(pw) map { extras =>
      pw.toFullEntry(extras)
    }
  }

  /** Load all dependents for a set of password entries.
    *
    * @param entries the password entries
    */
  private def loadDependents(entries: Set[PasswordEntry])
                            (implicit session: SlickSession):
    Future[Set[FullPasswordEntry]] = {

    DAO.passwordEntryExtraFieldsDAO.findForPasswordEntries(entries) map { set =>
      set map {
        case (passwordEntry, extras) => passwordEntry.toFullEntry(extras)
      }
    }
  }
}
