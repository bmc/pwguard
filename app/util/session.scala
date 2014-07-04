/** Simple session management layer, minus the part the interacts with the
  * browser.
  */
package util.session

import org.joda.time.{Duration, DateTime}
import org.apache.commons.math3.random.RandomDataGenerator
import java.security.SecureRandom
import scala.util.Random

/** Session cookie information.
  *
  * @param userIdentifier  user's unique ID, as a string. Note that this value
  *                        can be a stringified numeric ID, a username, an
  *                        email address, etc.
  * @param sessionID       user's session ID
  * @param ipAddress       user's IP address
  * @param validUntil      expiration date/time
  */
case class SessionData(userIdentifier: String,
                       sessionID:      String,
                       ipAddress:      String,
                       validUntil:     DateTime)

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
                validUntil     = DateTime.now.plus(duration))
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
  def getSessionData(sessionID: String): Either[String, Option[SessionData]]

  /** Store session data for a session ID. Any existing data for the
    * associated session ID is overwritten.
    *
    * @param sessionData the `SessionData` object to store
    *
    * @return `Right(true)` on successful store, `Left(error)` on error.
    */
  def storeSessionData(sessionData: SessionData): Either[String, Boolean]

  /** Clear or invalidate session data for a particular session ID.
    *
    * @param sessionID
    */
  def clearSessionData(sessionID: String): Unit
}

/** An in-memory, Map-driven session store. Note that this store cannot
  * save its session data across reboots of the server.
  */
class MemorySessionStore extends SessionStore {
  import scala.collection.mutable.{Map => MutableMap, HashMap => MutableHashMap}

  private val data: MutableMap[String, SessionData] =
    new MutableHashMap[String, SessionData]

  def getSessionData(sessionID: String): Either[String, Option[SessionData]] = {
    Right(data.get(sessionID))
  }

  def storeSessionData(sessionData: SessionData): Either[String, Boolean] = {
    data += (sessionData.sessionID -> sessionData)
    Right(true)
  }

  def clearSessionData(sessionID: String) {
    data -= sessionID
  }
}
