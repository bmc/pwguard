package dbservice

import models.BaseModel

import play.api.Logger

import pwguard.global.Globals.ExecutionContexts.DB._

import scala.reflect.runtime.{universe => ru}
import scala.concurrent.Future
import scala.util.control.NonFatal

class DAOException(msg: String) extends Exception(msg)

/** Base data access object. All DAOs should extend this class.
  *
  * @param dal    the instantiated Slick DAL
  * @param logger the logger to use
  * @tparam M     the type of the model the DAO loads

  */
abstract class BaseDAO[M <: BaseModel](val dal: DAL, val logger: Logger) {
  import dal.profile.simple._
  import scala.slick.jdbc.JdbcBackend

  type SlickSession = JdbcBackend#Session

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
      model.id.map { _ => update(model) }.getOrElse { insert(model) }
    }
  }

  /** Delete an instance of this model, by ID. All DAOs must provide this
    * method.
    *
    * @param id  the ID
    *
    * @return `Future(true)`
    */
  def delete(id: Int): Future[Boolean] = {
    withTransaction { implicit session =>
      Future {
        queryByID(id).delete
        true
      }
    }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  /** Get the Slick query that retrieves a model object by ID.
    *
    * @param id  the ID
    *
    * @return the query
    */
  protected def queryByID(id: Int): Query[Table[M], M]

  /** Insert an instance of the model. Must be supplied by subclasses.
    *
    * @param model   the model object to save
    * @param session the active session
    *
    * @return `Left(error)` on error, `Right(model)` (with a possibly-updated
    *         model) on success.
    */
  protected def insert(model: M)(implicit session: SlickSession): Future[M]

  /** Update an instance of the model. Must be supplied by subclasses.
    *
    * @param model   the model object to save
    * @param session the active session
    *
    * @return `Left(error)` on error, `Right(model)` (with a possibly-updated
    *         model) on success.
    */
  protected def update(model: M)(implicit session: SlickSession): Future[M]

  /** Run the specified code within a session.
    *
    * @param code  The code to run
    * @tparam T    The type parameter
    * @return      Whatever the code block returns
    */
  protected def withSession[T](code: SlickSession => Future[T]): Future[T] = {
    import pwguard.global.Globals.DB

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

  /** Execute a block of code within a Slick transaction, committing the
    * transaction if the code completes normally and rolling the transaction
    * back if the code throws any kind of exception.
    *
    * @param code  The partial function to run. The function takes a
    *        Slick Session object (and should mark it as implicit)
    *        and returns an `Either`.
    * @tparam T    The type for the `Right` part of the `Either`.
    * @return      `Left(error)` if an exception occurs or if the code block
    *               returns a `Left`; `Right` otherwise.
    */
  protected def withTransaction[T](code: SlickSession => Future[T]):
    Future[T] = {

    withSession { implicit session =>
      session.withTransaction {
        code(session)
      } recoverWith {
        case NonFatal(e) => {
          logger.error("Error during transaction", e)
          session.rollback()
          Future.failed(e)
        }
      }
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
  protected def loadOneModel[T, D](query: Query[T, D])
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
  protected def loadDependentIDs[M <: BaseModel: ru.TypeTag]
    (idSet: Set[Int], dao: BaseDAO[M])(implicit session: SlickSession):
    Future[Map[Int, M]] = {

    if (idSet.isEmpty)
      Future.successful(Map.empty[Int, M])

    else {
      dao.findByIDs(idSet).flatMap { loaded =>
        val loadedIDs = loaded.map {_.id.get}
        if (loadedIDs != idSet) {
          val name = typeName(ru.typeTag[M])
          logger.error {
            s"Missing some ${name} results in loaded appointments. " +
            s"Expected: $idSet, got: $loadedIDs"
          }
          daoError(s"Missing some ${name} results in loaded appointments.")
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

  /** Get the printable name of a runtime type.
   */
  private def typeName[T](tag: ru.TypeTag[T]): String = {
    tag.tpe.toString.split("""\.""").last
  }
}
