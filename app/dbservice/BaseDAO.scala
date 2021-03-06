package dbservice

import java.sql.Connection

import models.BaseModel

import play.api.Logger

import pwguard.global.Globals.ExecutionContexts.DB._
import util.ReflectionHelpers

import scala.reflect.runtime.{universe => ru}
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

class DAOException(msg: String) extends Exception(msg)

/** Base data access object. All DAOs should extend this class.
  *
  * @param dal    the instantiated Slick DAL
  * @param logger the logger to use
  * @tparam M     the type of the model the DAO loads

  */
abstract class BaseDAO[M <: BaseModel: ru.TypeTag](val dal:    DAL,
                                                   val logger: Logger) {
  import dal.ModelTable
  import dal.profile.simple._
  import scala.slick.jdbc.JdbcBackend
  import pwguard.global.Globals.DB

  type SlickSession = JdbcBackend#Session

  val modelName = ReflectionHelpers.typeTagShortName(ru.typeTag[M])

  // --------------------------------------------------------------------------
  // Public methods
  // ------------------------------------------------------------------------

  /** Find an instance of the underlying model.
    *
    * @param id  the ID
    *
    * @return `Future(Some(model))` if found, `Future(None)` if not
    */
  def findByID(id: Int): Future[Option[M]] = {
    withTransaction { implicit session =>
      loadOneModel(queryByID(id))
    }
  }

  /** Find one or more instances of the underlying model, by their IDs. All
    * DAOs must provide this method.
    *
    * @param idSet the IDs
    *
    * @return `Future(Set[model])`
    */
  def findByIDs(idSet: Set[Int]): Future[Set[M]]

  /** Save an instance of this model to the database.
    *
    * @param model  the model object to save
    *
    * @return `Future(model)`, with a possibly changed model object
    */
  def save(model: M): Future[M] = {
    withTransaction { implicit session: SlickSession =>
      Future {
        doSave(model).get
      }
    }
  }

  /** Save an instance of this model, synchronously. Use with care.
    *
    * @param model  the model object to save
    *
    * @return a `Try` of the possibly modified, saved model object
    */
  def saveSync(model: M): Try[M] = {
    withTransactionSync { implicit session: SlickSession =>
      doSave(model)
    }
  }

  /** Delete an instance of this model, by ID.
    *
    * @param id  the ID
    *
    * @return `Future(true)`
    */
  def delete(id: Int): Future[Boolean] = {
    withTransaction { implicit session =>
      deleteInSession(id)
    }
  }

  /** Delete many instances of this model.
    *
    * @param models  the models
    *
    * @return A `Future` of the number of items deleted
    */
  def deleteMany(models: Set[M]): Future[Int] = {
    withTransaction { implicit session =>
      val ids = models.flatMap(_.id)
      val q = for { m <- baseQuery if m.id inSet ids } yield m
      Future { q.delete }
    }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  /** Issue a delete outside a transaction. Useful for rolling into another
    * method that already has a session or transaction defined.
    *
    * @param id the ID of the model
    *
    * @return `Future(true)`
    */
  protected def deleteInSession(id: Int)(implicit session: SlickSession):
    Future[Boolean] = {

    Future {
      queryByID(id).delete
      true
    }
  }

  /** Issue a save outside a transaction. Useful for rolling into another
    * method that already has a session or transaction defined.
    *
    * @param model the model object to save
    *
    * @return A `Future` of the saved model object
    */
  protected def saveInSession(model: M)(implicit session: SlickSession):
    Future[M] = {

    Future {
      doSave(model).get
    }
  }

  /** Issue a saveSync outside a transaction. Useful for rolling into another
    * method that already has a session or transaction defined.
    *
    * @param model the model object to save
    *
    * @return A `Trye` of the saved model object
    */
  protected def saveSyncInSession(model: M)(implicit session: SlickSession):
    Try[M] = {

    Try {
      doSave(model).get
    }
  }

  /** Get the Slick query that retrieves a model object by ID.
    *
    * @param id  the ID
    *
    * @return the query
    */
  protected def queryByID(id: Int): Query[ModelTable[M], M, Seq]

  /** The base query for this model.
    *
    * @return the query
    */
  protected def baseQuery: Query[ModelTable[M], M, Seq]

  /** Insert an instance of the model. Must be supplied by subclasses.
    *
    * @param model   the model object to save
    * @param session the active session
    *
    * @return A `Try` of the result.
    */
  protected def insert(model: M)(implicit session: SlickSession): Try[M]

  /** Workhorse insert method. This method does an insert, using the result
    * of `baseQuery`. However, it cannot update the model object (because
    * `BaseModel` is a trait, not a case class. Thus, all subclasses still
    * must provide `insert()`. This method just eases the implementation,
    * which can usually be as simple as:
    *
    * {{{
    *   protected def insert(model: SomeModel)(implicit session: SlickSession):
    *     Try[SomeModel] = {
    *
    *     doInsert(model) map { id => model.copy(id = Some(id)) }
    *   }
    * }}}
    *
    * @param model   the model
    * @param session the active session
    *
    * @return A `Try` of the result.
    */
  protected def doInsert(model: M)(implicit session: SlickSession): Try[Int] = {
    Try {
      // Note: The <query>.insert() method returns the number of items
      // inserted, NOT the ID.
      val id = (baseQuery returning baseQuery.map(_.id)) += model
      logger.debug(s"Insert of ${modelName} returned ID $id")
      id
    }
  }

  /** Update an instance of the model. Must be supplied by subclasses.
    *
    * @param model   the model object to save
    * @param session the active session
    *
    * @return A `Try` of the result.
    */
  protected def update(model: M)(implicit session: SlickSession): Try[M]

  /** Run the specified code within a session.
    *
    * @param code  The code to run
    * @tparam T    The code's return type
    * @return      Whatever the code block returns
    */
  protected def withSession[T](code: SlickSession => Future[T]): Future[T] = {

    val session = DB.createSession()
    code(session) map { result =>
      session.close()
      result
    } recoverWith {
      case NonFatal(e) => {
        session.close()
        Future.failed(e)
      }
    }
  }

  /** Run the specified code within a session, synchronously.
    *
    * @param  code  The code to run
    * @tparam T     The code's return type
    * @return       whatever the code block returns
    */
  protected def withSessionSync[T](code: SlickSession => Try[T]): Try[T] = {
    val session = DB.createSession()
    code(session) map { result =>
      session.close()
      result
    } recoverWith {
      case NonFatal(e) => {
        session.close()
        Failure(e)
      }
    }
  }

  /** Execute a block of code within a Slick transaction, committing the
    * transaction if the code completes normally and rolling the transaction
    * back if the code throws any kind of exception.
    *
    * @param code  The partial function to run. The function takes a
    *              Slick Session object (and should mark it as implicit)
    *              and returns a `Future`
    * @tparam T    The code's return type
    * @return      whatever the code returns, or a failed future on exception
    */
  protected def withTransaction[T](code: SlickSession => Future[T]):
    Future[T] = {

    // We manage this ourselves, because a glance at the Slick session
    // source shows that it most likely doesn't handle futures properly.
    // It could commit the transaction, or roll it back, before the
    // future completes.
    implicit val session = DB.createSession()

    val conn = session.conn
    conn.setAutoCommit(false)
    code(session) andThen {
      case Failure(e) => {
        logger.error("Error during transaction", e)
        conn.rollback()
      }
    } andThen {
      case _ => {
        conn.setAutoCommit(true)
        session.close()
      }
    }
  }

  /** Execute a block of code within a Slick transaction, committing the
    * transaction if the code completes normally and rolling the transaction
    * back if the code throws any kind of exception. Instead of running in
    * a future, this version runs synchronously.
    *
    * @param code  The partial function to run. The function takes a
    *              Slick Session object (and should mark it as implicit)
    *              and returns a `Try`
    * @tparam T    The code's return type
    * @return      A `Try` containing the result (`Success`) or the exception
    *              (`Failure`)
    */
  protected def withTransactionSync[T](code: SlickSession => Try[T]):
    Try[T] = {

    withSessionSync { session =>
      session.withTransaction {
        code(session)
      }
    }
  }

  /** Execute a block of code within a Slick connection, ensuring that the
    * connection is closed.
    *
    * @param code  the code to run. The connection is passed.
    * @tparam T    what the code returns (wrapped in a Future)
    *
    * @return the result of code, or a failed future
    */
  protected def withConnection[T](code: Connection => Future[T]): Future[T] = {
    withSession { implicit session =>
      code(session.conn)
    }
  }

  /** Load one model object, without mapping it. If the query results in more
    * than one result row, this method flags an error.
    *
    * @param query   The Slick query to issue
    * @param session The current Slick session
    * @tparam T       The Slick table on which to make the query
    * @tparam D       The database model type
    *
    * @return `Right[Option(model)]` on success, `Left(error)` on error.
    */
  protected def loadOneModel[T, D](query: Query[T, D, Seq])
                                  (implicit session: SlickSession):
    Future[Option[D]] = {

    Future {
      val results = query.list
      results.length match {
        case 0 => None
        case 1 => Some(results(0))
        case _ => daoError(s"Got ${results.length} objects for query. Expected 1")
      }
    }
  }

  /** Useful when loading dependents, this method takes a set of IDs and
    * a DAO, attempts to load the corresponding database objects, and returns
    * an error if all IDs are not present in the result set.
    *
    * @param  idSet  the set of IDs to load
    * @param  dao    the DAO to use to load the objects
    * @tparam M      the underlying data model object
    *
    * @return On success, this method returns a `Right` containing a map
    *         of ID to model. On failure, it returns `Left(error)`.
    */
  protected def loadDependentIDs(idSet: Set[Int], dao: BaseDAO[M])
                                (implicit session: SlickSession):
    Future[Map[Int, M]] = {

    if (idSet.isEmpty)
      Future.successful(Map.empty[Int, M])

    else {
      dao.findByIDs(idSet).flatMap { loaded =>
        val loadedIDs = loaded.map {_.id.get}
        if (loadedIDs != idSet) {
          logger.error {
            s"Missing some ${modelName} results in loaded appointments. " +
            s"Expected: $idSet, got: $loadedIDs"
          }
          daoError(s"Missing some ${modelName} results in loaded appointments.")
        }
        else {
          Future {
            (Map(loaded.toSeq.map { m => m.id.get -> m}: _* ))
          }
        }
      }
    }
  }

  protected def daoError(msg: String): Nothing = throw new DAOException(msg)

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  /** Workhorse save method, called by all the others.
    *
    * @param model   model to save
    * @param session existing session
    *
    * @return a `Try` of the saved model
    */
  private def doSave(model: M)(implicit session: SlickSession): Try[M] = {
    model.id.map { _ => update(model) }.getOrElse { insert(model) }
  }

}
