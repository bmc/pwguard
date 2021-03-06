package dbservice

import models._
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
  import dal.{PasswordEntriesTable, PasswordEntries, PasswordEntryKeywords}
  import DAO.{passwordEntryExtraFieldsDAO,
              passwordEntryKeywordsDAO,
              passwordEntrySecurityQuestionsDAO}

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
    * @param user  the user
    *
    * @return A future containing the results, or a failed future.
    */
  def allForUser(user: User): Future[Set[PasswordEntry]] = {
    withSession { implicit session =>
      user.id.map { userID =>
        Future { compiledAllQuery(userID).run.toSet }
      } getOrElse {
        Future.successful(Set.empty[PasswordEntry])
      }
    }
  }

  /** Get the count of the number of password entries for a user.
    *
    * @param user  the user
    *
    * @return A future containing the results, or a failed future.
    */
  def totalForUser(user: User): Future[Int] = {
    withSession { implicit session =>
      user.id.map { userID =>
        Future {
          val q = for { pw <- PasswordEntries if pw.userID === userID }
                  yield pw
          q.list.size
        }
      } getOrElse {
        Future.successful(0)
      }
    }
  }

  /** Get the count of the number of password entries for a set of users.
    *
    * @param users the users
    *
    * @return A future containing `(User, count)` pairs.
    */
  def totalsForUsers(users: Set[User]): Future[Seq[(User, Int)]] = {
    withSession { implicit session =>
      val userIDs = users.flatMap { _.id }.toSet

      val q = for { pw <- PasswordEntries if pw.userID inSet userIDs }
              yield pw
      val q2 = q.groupBy(_.userID).map { case (userID, entries) =>
        (userID, entries.length)
      }

      Future {
        val countsByUser = q2.list.toMap

        users.toSeq map { u: User =>
          val uid = u.id.getOrElse(-1)
          (u, countsByUser.getOrElse(uid, 0))
        }
      }
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

      // There's a way to do this with one query. I'll figure it out later.

      def getKeywordMatches(): Future[Set[Int]] = {
        val q =
          for { pwe <- PasswordEntries if pwe.userID === userID
                kw  <- PasswordEntryKeywords if (kw.passwordEntryID === pwe.id) &&
                                                (kw.keyword like sqlTerm) }
          yield pwe.id

        Future { q.list.toSet }
      }

      def getEntryMatches(keywordMatchIDs: Set[Int]): Future[Set[PasswordEntry]] = {

        val q1 = PasswordEntries.filter { pw =>
          (pw.name.toLowerCase like sqlTerm) || (pw.id inSet keywordMatchIDs)
        }

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

        Future {
          qFinal.list.toSet
        }
      }

      for { kwMatchIDs <- getKeywordMatches()
            pwEntries  <- getEntryMatches(kwMatchIDs) }
      yield pwEntries
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
        deleteDependents(actualIDs) flatMap { n =>
          val qDel = for { pw <- PasswordEntries if pw.id inSet actualIDs }
                     yield pw
          Future { qDel.delete }
        }
      }
    }
  }

  /** Save a full password entry, with all its dependents.
    *
    * @param entry  the full password entry
    *
    * @return A future of the saved entry, which may be modified
    */
  def saveWithDependents(entry: FullPasswordEntry):
    Future[FullPasswordEntry] = {

    withTransaction { implicit session =>
      val baseEntry = entry.toBaseEntry

      for { savedBase <- save(baseEntry)
            full       = savedBase.toFullEntry()
            saved1    <- saveExtraFieldChanges(full, entry.extraFields)
            saved2    <- saveKeywordChanges(saved1, entry.keywords)
            saved3    <- saveSecurityQuestionChanges(saved2, entry.securityQuestions) }
      yield saved3
    }
  }

  override def delete(id: Int): Future[Boolean] = {
    withTransaction { implicit session =>
      deleteDependents(id) flatMap { n =>
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

  private def saveExtraFieldChanges(pwEntry: FullPasswordEntry,
                                    extras:  Set[PasswordEntryExtraField])
                                   (implicit session: SlickSession):
    Future[FullPasswordEntry] = {

    val extrasDAO = DAO.passwordEntryExtraFieldsDAO

    // This logic isn't 100% correct. A change in a field name will
    // cause it to appear to be new, because the name is the only hash
    // code. That's not optimal, but it isn't worth optimizing right now.
    // The simplest solution, the one that has the least possibility of
    // going wrong, is just to delete all the old entries and (re-)insert
    // the new ones.
    extrasDAO.deleteForPasswordEntry(pwEntry.id.get) flatMap { n =>
      // Make sure we associate the extras with this password entry.
      val adjustedExtras = extras.map {
        _.copy(id = None, passwordEntryID = pwEntry.id)
      }

      extrasDAO.saveMany(adjustedExtras) map { savedExtras =>
        pwEntry.copy(extraFields = savedExtras)
      }
    }
  }

  private def saveKeywordChanges(pwEntry:  FullPasswordEntry,
                                 keywords: Set[PasswordEntryKeyword])
                                (implicit session: SlickSession):
    Future[FullPasswordEntry] = {

    val kwDAO = passwordEntryKeywordsDAO

    // Keep it simple for now.
    kwDAO.deleteForPasswordEntry(pwEntry.id.get) flatMap { n =>
      val adjustedKeywords = keywords map {
        _.copy(id = None, passwordEntryID = pwEntry.id)
      }

      kwDAO.saveMany(adjustedKeywords) map { savedKeywords =>
        pwEntry.copy(keywords = savedKeywords)
      }
    }
  }

  private def saveSecurityQuestionChanges(pwEntry:   FullPasswordEntry,
                                          questions: Set[PasswordEntrySecurityQuestion])
                                         (implicit session: SlickSession):
    Future[FullPasswordEntry] = {

    val sqDAO = passwordEntrySecurityQuestionsDAO

    // Keep it simple for now
    sqDAO.deleteForPasswordEntry(pwEntry.id.get) flatMap { n =>
      val adjustedQuestions = questions map {
        _.copy(id = None, passwordEntryID = pwEntry.id)
      }

      sqDAO.saveMany(adjustedQuestions) map { savedQuestions =>
        pwEntry.copy(securityQuestions = savedQuestions)
      }
    }
  }

  private def deleteDependents(ids: Set[Int])
                              (implicit session: SlickSession): Future[Int] = {
    for { n1 <- passwordEntryExtraFieldsDAO.deleteForPasswordEntries(ids)
          n2 <- passwordEntryKeywordsDAO.deleteForPasswordEntries(ids)
          n3 <- passwordEntrySecurityQuestionsDAO.deleteForPasswordEntries(ids) }
    yield n1 + n2 + n3
  }

  private def deleteDependents(id: Int)
                              (implicit session: SlickSession): Future[Int] = {
    for { n1 <- passwordEntryExtraFieldsDAO.deleteForPasswordEntry(id)
          n2 <- passwordEntryKeywordsDAO.deleteForPasswordEntry(id)
          n3 <- passwordEntrySecurityQuestionsDAO.deleteForPasswordEntry(id) }
    yield n1 + n2 + n3
  }

  private def loadDependents(pw: PasswordEntry)
                            (implicit session: SlickSession):
    Future[FullPasswordEntry] = {

    for { extras <- passwordEntryExtraFieldsDAO.findForPasswordEntry(pw)
          kw     <- passwordEntryKeywordsDAO.findForPasswordEntry(pw)
          q      <- passwordEntrySecurityQuestionsDAO.findForPasswordEntry(pw) }
    yield pw.toFullEntry(extras            = extras,
                         keywords          = kw,
                         securityQuestions = q)
  }

  /** Load all dependents for a set of password entries.
    *
    * @param entries the password entries
    */
  private def loadDependents(entries: Set[PasswordEntry])
                            (implicit session: SlickSession):
    Future[Set[FullPasswordEntry]] = {

    for {
      exMap <- passwordEntryExtraFieldsDAO.findForPasswordEntries(entries)
      kwMap <- passwordEntryKeywordsDAO.findForPasswordEntries(entries)
      sqMap <- passwordEntrySecurityQuestionsDAO.findForPasswordEntries(entries)
    }
    yield {
      entries map { entry =>
        val extras = exMap.getOrElse(entry, Set.empty[PasswordEntryExtraField])
        val keywords = kwMap.getOrElse(entry, Set.empty[PasswordEntryKeyword])
        val questions = sqMap.getOrElse(entry, Set.empty[PasswordEntrySecurityQuestion])
        entry.toFullEntry(extras            = extras,
                          keywords          = keywords,
                          securityQuestions = questions)
      }
    }
  }
}
