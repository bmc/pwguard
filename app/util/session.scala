/** Simple session management layer, minus the part the interacts with the
  * browser.
  */
package util.session

import org.joda.time.{Duration, DateTime}
import org.apache.commons.math3.random.RandomDataGenerator
import util.EitherOptionHelpers.Implicits._
import pwguard.global.Globals.ExecutionContexts.Default._

import scala.concurrent.Future

/** Session cookie information.
  *
  * @param userIdentifier  user's unique ID, as a string. Note that this value
  *                        can be a stringified numeric ID, a username, an
  *                        email address, etc.
  * @param sessionID       user's session ID
  * @param ipAddress       user's IP address
  * @param validUntil      expiration date/time
  * @param duration        stored session duration
  */
case class SessionData(userIdentifier: String,
                       sessionID:      String,
                       ipAddress:      String,
                       validUntil:     DateTime,
                       duration:       Duration)

/** Utility methods.
  */
object SessionUtil {
  private val rdg = new RandomDataGenerator()

  /** Generate new session data, with a random session ID.
    *
    * @param userIdentifier  the user identifier to store
    * @param ipAddress       the user's IP address
    * @param duration        How long the session data should be valid. This
    *                        value is used to calculate the expiration time,
    *                        from the current time.
    *
    * @return a suitable `SessionData` object, with a generated session ID.
    */
  def newSessionData(userIdentifier: String,
                     ipAddress:      String,
                     duration:       Duration): SessionData = {
    val now       = DateTime.now()
    val expiry    = now.plus(duration)
    val sessionID = rdg.nextSecureHexString(32)

    SessionData(userIdentifier = userIdentifier,
                sessionID      = rdg.nextSecureHexString(32),
                ipAddress      = ipAddress,
                validUntil     = DateTime.now.plus(duration),
                duration       = duration)
  }
}

/** Defines the interface for a session store. Session stores can be anything
  * from memory to a database.
  */
trait SessionStore {

  /** Retrieve session data for a session ID.
    *
    * @param sessionID  the session ID
    *
    * @return `Right(Some(data))` if found. `Right(None)` if not found.
    *         `Left(error)` on error.
    */
  def getSessionData(sessionID: String): Future[Option[SessionData]]

  /** Store session data for a session ID. Any existing data for the
    * associated session ID is overwritten.
    *
    * @param sessionData the `SessionData` object to store
    *
    * @return `Right(true)` on successful store, `Left(error)` on error.
    */
  def storeSessionData(sessionData: SessionData): Future[Boolean]

  /** Clear or invalidate session data for a particular session ID.
    *
    * @param sessionID
    */
  def clearSessionData(sessionID: String): Future[Boolean]

  /** Refresh a session, updating its timestamp.
    *
    * @param sessionID  the session ID
    *
    * @return `Right(SessionData)` on successful refresh, `Left(error)` on error.
    */
  def refresh(sessionID: String): Future[SessionData] = {
    for { dataOpt   <- getSessionData(sessionID)
          data      <- dataOpt.toFuture("Nonexistent session $sessionID")
          refreshed <- refresh(data) }
    yield refreshed
  }

  /** Refresh a session, updating its timestamp.
    *
    * @param data  the session data to update and replace
    *
    * @return `Right(SessionData)` on successful refresh, `Left(error)` on error.
    */
  def refresh(data: SessionData): Future[SessionData] = {
    val newData = data.copy(validUntil = DateTime.now.plus(data.duration))
    storeSessionData(newData).map { _ => newData }
  }
}

/** An in-memory, Map-driven session store. Note that this store cannot
  * save its session data across reboots of the server.
  */
class MemorySessionStore extends SessionStore {
  import scala.collection.mutable.{Map => MutableMap, HashMap => MutableHashMap}

  private val data: MutableMap[String, SessionData] =
    new MutableHashMap[String, SessionData]

  def getSessionData(sessionID: String): Future[Option[SessionData]] = {
    Future { data.get(sessionID) }
  }

  def storeSessionData(sessionData: SessionData): Future[Boolean] = {
    Future {
      data += (sessionData.sessionID -> sessionData)
      true
    }
  }

  def clearSessionData(sessionID: String): Future[Boolean] = {
    Future {
      data -= sessionID
      true
    }
  }
}
