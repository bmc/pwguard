package dbservice

import models.{PasswordEntrySecurityQuestion, PasswordEntry}
import play.api.Logger
import pwguard.global.Globals.ExecutionContexts.DB._
import scala.concurrent.Future
import scala.util.{Success, Failure, Try}

class PasswordEntrySecurityQuestionsDAO(_dal: DAL, _logger: Logger)
  extends BaseDAO[PasswordEntrySecurityQuestion](_dal, _logger) {

  override val logger = Logger("pwguard.dbservice.PasswordEntrySecurityQuestionsDAO")

  import dal.profile.simple._
  import dal.{PasswordEntrySecurityQuestionsTable, PasswordEntrySecurityQuestions}

  private type PWEntrySecurityQuestionsQuery =
    Query[PasswordEntrySecurityQuestionsTable,
          PasswordEntrySecurityQuestion,
          Seq]

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Find all password entry security question objects with the specified IDs.
    *
    * @param idSet the IDs
    *
    * @return A future containing the results, or a failed future.
    */
  def findByIDs(idSet: Set[Int]): Future[Set[PasswordEntrySecurityQuestion]] = {
    withSession { implicit session =>
      val q = for (p <- PasswordEntrySecurityQuestions if p.id inSet idSet)
              yield p
      Future { q.list.toSet }
    }
  }

  // --------------------------------------------------------------------------
  // Package-visible methods
  // ------------------------------------------------------------------------

  /** Find all password entry extra field objects for a given password
    * entry record. Intended only for use within this package, this method
    * assumes the existence of a session.
    *
    * @param pwe     the password entry
    * @param session the session
    *
    * @return a future containing the results, or a failed future
    */
  private[dbservice] def findForPasswordEntry(pwe: PasswordEntry)
                                             (implicit session: SlickSession):
    Future[Set[PasswordEntrySecurityQuestion]] = {

    Future {
      loadForPasswordEntry(pwe)
    }
  }

  /** Load all extras for a set of password entries. To prevent SQLite
    * lock issues, this function loads them serially, returning a future
    * that completes when all are loaded. It also assumes that's there's an
    * existing (implicit) session.
    *
    * @param entries the password entries
    * @param session the existing session
    *
    * @return a `Future` containing a set of 2-tuples, each consisting of a
    *         `PasswordEntry` and its extra field records.
    */
  private[dbservice] def findForPasswordEntries(entries: Set[PasswordEntry])
                                               (implicit session: SlickSession):
    Future[Map[PasswordEntry, Set[PasswordEntrySecurityQuestion]]] = {

    Future {
      val entryIDs = entries.collect {
        case p: PasswordEntry if p.id.isDefined => p.id.get
      }

      val q = for { e <- PasswordEntrySecurityQuestions
                    if e.passwordEntryID inSet entryIDs }
              yield e

      val extrasMap = q.list.groupBy(_.passwordEntryID.get)
      val noExtras = Set.empty[PasswordEntrySecurityQuestion]
      entries.map { entry =>
        entry.id.map { id =>
          (entry, extrasMap.getOrElse(id, noExtras).toSet)
        } getOrElse {
          (entry, noExtras)
        }
      }.toMap
    }
  }

  /** Save many records. Only intended to be called within this layer.  The
    * caller must define the session/transaction, to ensure that this call is
    * contained within a larger existing session or transaction.
    *
    * @param entries  the entries to be saved
    * @param session  an open session
    *
    * @return A future of the saved entries
    */
  private[dbservice] def saveMany(entries: Set[PasswordEntrySecurityQuestion])
                                 (implicit session: SlickSession):
    Future[Set[PasswordEntrySecurityQuestion]] = {

    Future {
      val tries = entries map { saveSyncInSession(_) }

      val errors: Set[Throwable] = tries.collect {
        case Failure(e) => e
      }

      val successes: Set[PasswordEntrySecurityQuestion] = tries.collect {
        case Success(p) => p
      }

      if (! errors.isEmpty) {
        throw errors.head
      }

      successes
    }
  }

  /** Delete all extras for a password entry. Only intended to be called
    * within this layer. The caller must define the session/transaction, to
    * ensure that this call is contained within a larger existing session or
    * transaction.
    *
    * @param pwe      the password entry
    * @param session  an open session
    *
    * @return A future of the number of deleted entries
    */
  private[dbservice] def deleteForPasswordEntry(pwe: PasswordEntry)
                                               (implicit session: SlickSession):
    Future[Int] = {

    pwe.id.map { deleteForPasswordEntry(_) }.getOrElse { Future.successful(0) }
  }

  /** Delete all extras for a password entry. Only intended to be called
    * within this layer. The caller must define the session/transaction, to
    * ensure that this call is contained within a larger existing session or
    * transaction.
    *
    * @param id       the ID of the password entry
    * @param session  an open session
    *
    * @return A future of the number of deleted entries
    */
  private[dbservice] def deleteForPasswordEntry(id: Int)
                                               (implicit session: SlickSession):
    Future[Int] = {

    val q = for { p <- PasswordEntrySecurityQuestions if p.passwordEntryID === id }
    yield p

    Future { q.delete }
  }

  /** Delete all extras for many password entries. Only intended to be
    * called within this layer. The caller must define the session/transaction,
    * to ensure that this call is contained within a larger existing session or
    * transaction.
    *
    * @param ids   the IDs of the password entries
    * @param sess  an open session
    *
    * @return A future of the number of deleted entries
    */
  private[dbservice] def deleteForPasswordEntries(ids: Set[Int])
                                                 (implicit sess: SlickSession):
    Future[Int] = {

    val q = for { p <- PasswordEntrySecurityQuestions if p.passwordEntryID inSet ids }
    yield p
    Future { q.delete }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  protected def queryByID(id: Int): PWEntrySecurityQuestionsQuery = {
    for (p <- PasswordEntrySecurityQuestions if p.id === id) yield p
  }

  protected val baseQuery = PasswordEntrySecurityQuestions

  protected def insert(item: PasswordEntrySecurityQuestion)
                      (implicit session: SlickSession):
  Try[PasswordEntrySecurityQuestion] = {

    doInsert(item) map { id => item.copy(id = Some(id)) }
  }

  protected def update(item: PasswordEntrySecurityQuestion)
                      (implicit session: SlickSession):
  Try[PasswordEntrySecurityQuestion] = {

    Try {
      val q = for { p <- PasswordEntrySecurityQuestions if p.id === item.id.get }
              yield (p.question, p.answer)
      q.update((item.question, item.answer))
      item
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def loadForPasswordEntry(pwe: PasswordEntry)
                                  (implicit session: SlickSession):
    Set[PasswordEntrySecurityQuestion] = {

    val q = for (p <- PasswordEntrySecurityQuestions
                 if p.passwordEntryID === pwe.id.get) yield p

    q.list.toSet
  }
}
