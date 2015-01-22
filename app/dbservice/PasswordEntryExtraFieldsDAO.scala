package dbservice

import models.{PasswordEntryExtraField, PasswordEntry}
import play.api.Logger
import pwguard.global.Globals.ExecutionContexts.DB._
import scala.concurrent.Future
import scala.util.Try

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

  protected def insert(item: PasswordEntryExtraField)
                      (implicit session: SlickSession):
    Future[PasswordEntryExtraField] = {

    Future {
      val id = PasswordEntryExtraFields.insert(item)
      item.copy(id = Some(id))
    }
  }

  protected def update(item: PasswordEntryExtraField)
                      (implicit session: SlickSession):
  Future[PasswordEntryExtraField] = {

    Future {
      val q = for { p <- PasswordEntryExtraFields if p.id === item.id.get }
              yield (p.fieldName, p.fieldValue, p.passwordEntryID)
      q.update((item.fieldName, item.fieldValue, item.passwordEntryID))
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
