package dbservice

import models.{FullPasswordEntry, PasswordEntryExtraField, PasswordEntry}
import play.api.Logger
import pwguard.global.Globals.ExecutionContexts.DB._
import scala.concurrent.Future
import scala.util.{Success, Failure, Try}

class PasswordEntryExtraFieldsDAO(_dal: DAL, _logger: Logger)
  extends BaseDAO[PasswordEntryExtraField](_dal, _logger){

  override val logger = Logger("dbservice.PasswordEntryExtraFieldsDAO")

  import dal.profile.simple._
  import dal.{PasswordEntryExtraFieldsTable, PasswordEntryExtraFields}

  private type PWEntryExtraFieldsQuery = Query[PasswordEntryExtraFieldsTable,
                                               PasswordEntryExtraField,
                                               Seq]

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Find all password entry extra field objects with the specified IDs.
    *
    * @param idSet the IDs
    *
    * @return A future containing the results, or a failed future.
    */
  def findByIDs(idSet: Set[Int]): Future[Set[PasswordEntryExtraField]] = {
    withSession { implicit session =>
      val q = for (p <- PasswordEntryExtraFields if p.id inSet idSet) yield p
      Future { q.list.toSet }
    }
  }

  /** Find all password entry extra field objects for a given password
    * entry record.
    *
    * @param pwe  the password entry
    *
    * @return a future containing the results, or a failed future
    */
  def findForPasswordEntry(pwe: PasswordEntry):
    Future[Set[PasswordEntryExtraField]] = {

    withSession { implicit session =>
      Future {
        loadForPasswordEntry(pwe)
      }
    }
  }


  /** Load all extras for a set of password entries. To prevent SQLite
    * lock issues, this function loads them serially, returning a future
    * that completes when all are loaded.
    *
    * @param entries the password entries
    *
    * @return a `Future` containing a set of 2-tuples, each consisting of a
    *         `PasswordEntry` and its extra field records.
    */
  def findForPasswordEntries(entries: Set[PasswordEntry]):
    Future[Set[(PasswordEntry, Set[PasswordEntryExtraField])]] = {

    Future {
      val tries: Seq[Try[(PasswordEntry, Set[PasswordEntryExtraField])]] =
        for (entry <- entries.toSeq) yield {
          findForPasswordEntrySync(entry) map { set =>
            (entry, set)
          }
        }

      // If there are any errors, fail with the first one.
      tries.filter { _.isFailure }.headOption map {
        case Failure(e) => throw e
        case Success(_) => throw new Exception("BUG: Expected Failure")
      }

      tries.filter { _.isSuccess }
           .map {
              case Success(tuple) => tuple
              case Failure(e)     => throw e
           }
           .toSet
    }
  }

  /** Save many records.
    *
    * @param entries  the entries to be saved
    *
    * @return A future of the saved entries
    */
  def saveMany(entries: Set[PasswordEntryExtraField]):
    Future[Set[PasswordEntryExtraField]] = {

    withTransaction { implicit session =>
      Future {
        val tries = entries map { saveSync(_) }

        // If there are any errors, fail with the first one.
        tries.filter { _.isFailure }.headOption map {
          case Failure(e) => throw e
          case Success(_) => throw new Exception("BUG: Expected Failure.")
        }

        tries.filter { _.isSuccess }
             .map {
               case Success(entry) => entry
               case Failure(e)     => throw e
             }
             .toSet
      }
    }
  }

  /** Delete all extras for a password entry.
    *
    * @param pwe the password entry
    *
    * @return A future of the number of deleted entries
    */
  def deleteForPasswordEntry(pwe: PasswordEntry): Future[Int] = {
    pwe.id.map { deleteForPasswordEntry(_) }
          .getOrElse(Future.successful(0))
  }

  /** Delete all extras for a password entry.
    *
    * @param id the ID of the password entry
    *
    * @return A future of the number of deleted entries
    */
  def deleteForPasswordEntry(id: Int): Future[Int] = {
    withTransaction { implicit session =>
      val q = for { p <- PasswordEntryExtraFields if p.passwordEntryID === id }
              yield p

      Future { q.delete }
    }
  }

  // --------------------------------------------------------------------------
  // Package-visible methods
  // ------------------------------------------------------------------------

  private[dbservice] def findForPasswordEntrySync(pwe: PasswordEntry):
    Try[Set[PasswordEntryExtraField]] = {

    withSessionSync { implicit session =>
      Try {
        loadForPasswordEntry(pwe)
      }
    }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  protected def queryByID(id: Int): PWEntryExtraFieldsQuery = {
    for (p <- PasswordEntryExtraFields if p.id === id) yield p
  }

  protected val baseQuery = PasswordEntryExtraFields

  protected def insert(item: PasswordEntryExtraField)
                      (implicit session: SlickSession):
    Try[PasswordEntryExtraField] = {

    Try {
      val id = PasswordEntryExtraFields.insert(item)
      item.copy(id = Some(id))
    }
  }

  protected def update(item: PasswordEntryExtraField)
                      (implicit session: SlickSession):
    Try[PasswordEntryExtraField] = {

    Try {
      val q = for { p <- PasswordEntryExtraFields if p.id === item.id.get }
              yield (p.fieldName, p.fieldValue, p.passwordEntryID)
      q.update((item.fieldName, item.fieldValue, item.passwordEntryID.get))
      item
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  private def loadForPasswordEntry(pwe: PasswordEntry)
                                  (implicit session: SlickSession):
    Set[PasswordEntryExtraField] = {

    val q = for (p <- PasswordEntryExtraFields
                 if p.passwordEntryID === pwe.id.get) yield p

    q.list.toSet
  }
}
