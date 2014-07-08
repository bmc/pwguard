package dbservice

import play.api.Logger
import models.BaseModel

import scala.reflect.runtime.{universe => ru}

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
    * @return `Right(model)` if found, `Left(error)` on error
    */
  def findByID(id: Int): Either[String, Option[M]]

  /** Find one or more instances of the underlying model, by their IDs. All
    * DAOs must provide this method.
    *
    * @param idSet the IDs
    *
    * @return `Right(Set[model])` on success, `Left(error)` on error.
    */
  def findByIDs(idSet: Set[Int]): Either[String, Set[M]]

  /** Save an instance of this model to the database.
    *
    * @param model  the model object to save
    *
    * @return `Right(model)`, with a possibly changed model object,
    *         on success. `Left(error)` on error.
    */
  def save(model: M): Either[String, M] = {
    withTransaction { implicit session: SlickSession =>
      model.id.map { _ => update(model) }.getOrElse { insert(model) }
    }
  }

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  /** Insert an instance of the model. Must be supplied by subclasses.
    *
    * @param model   the model object to save
    * @param session the active session
    *
    * @return `Left(error)` on error, `Right(model)` (with a possibly-updated
    *         model) on success.
    */
  protected def insert(model: M)(implicit session: SlickSession):
    Either[String, M]

  /** Update an instance of the model. Must be supplied by subclasses.
    *
    * @param model   the model object to save
    * @param session the active session
    *
    * @return `Left(error)` on error, `Right(model)` (with a possibly-updated
    *         model) on success.
    */
  protected def update(model: M)(implicit session: SlickSession):
    Either[String, M]

  /** Run the specified code within a session.
    *
    * @param code  The code to run
    * @tparam T    The type parameter
    * @return      Whatever the code block returns
    */
  protected def withSession[T](code: SlickSession => T): T = {
    import pwguard.global.Globals.DB

    DB withSession { implicit session => code(session) }
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
  protected def withTransaction[T](code: SlickSession => Either[String, T]):
    Either[String, T] = {

    withSession { implicit session =>
      session.withTransaction {
        try {
          code(session)
        }

        catch {
          case t: Throwable => {
            logger.error("Error during transaction", t)
            session.rollback()
            Left(t.getMessage)
          }
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
    Either[String, Option[D]] = {

    val results = query.list
    results.length match {
      case 0 => Right(None)
      case 1 => Right(Some(results(0)))
      case _ => Left(s"Got ${results.length} objects for query. Expected 1")
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
    Either[String, Map[Int, M]] = {

    if (idSet.isEmpty)
      Right(Map.empty[Int, M])
    else {
      dao.findByIDs(idSet).right.flatMap { loaded =>
        val loadedIDs = loaded.map {_.id.get}
        if (loadedIDs != idSet) {
          val name = typeName(ru.typeTag[M])
          logger.error {
            s"Missing some ${name} results in loaded appointments. " +
            s"Expected: $idSet, got: $loadedIDs"
          }
          Left(s"Missing some ${name} results in loaded appointments.")
        }
        else {
          Right(Map(loaded.toSeq.map { m => m.id.get -> m}: _* ))
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // Private methods
  // ------------------------------------------------------------------------

  /** Get the printable name of a runtime type.
   */
  private def typeName[T](tag: ru.TypeTag[T]): String = {
    tag.tpe.toString.split("""\.""").last
  }
}
