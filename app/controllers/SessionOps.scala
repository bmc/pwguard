package controllers

import dbservice.DAO
import models.User
import org.joda.time.{DateTime, Duration => JodaDuration}
import play.api.Play.current
import play.api.Logger
import play.api.mvc.Request
import util.session._
import util.FutureHelpers._
import util.EitherOptionHelpers.Implicits._
import scala.concurrent.Future
import scala.util.control.NonFatal
import pwguard.global.Globals.ExecutionContexts.Default._

/** Session-related stuff.
  */
object SessionOps {

  private val sessionStore   = new PlayCacheSessionStore
  private val logger         = Logger("pwguard.controllers.SessionOps")
  private val SessionTimeout = current.configuration
                                      .getMilliseconds("session.timeout")
                                      .orElse { Some(60 * 60 * 1000L) }
                                      .map { JodaDuration.millis(_) }
                                      .get

  val SessionKey  = "session_id"
  val IsMobileKey = "is_mobile"

  // --------------------------------------------------------------------------
  // Public methods
  // --------------------------------------------------------------------------

  /** Create new session data for a given user.
    *
    * @param request current request
    * @param user    the user
    * @tparam T      the request type (not used)
    *
    * @return `Left(error)` or `Right(sessionData)`
    */
  def newSessionDataFor[T](request: Request[T], user: User):
    Future[SessionData] = {

    val sessionData = SessionUtil.newSessionData(
      userIdentifier = user.email,
      ipAddress      = request.remoteAddress,
      duration       = SessionTimeout
    )

    sessionStore.storeSessionData(sessionData).map { _ =>
      sessionData
    }
  }

  /** Get the current session data.
    *
    * @param request current request
    * @tparam T      request type (not used)
    *
    * @return `Some(SessionData)` or `None`
    */
  def currentSessionData[T](request: Request[T]): Future[Option[SessionData]] = {
    getSession(request)
  }

  /** Determine who's logged in, taking session expiry, IP addresses, etc.,
    * into account.
    *
    * @param request current request
    * @tparam T      request type (not used)
    *
    * @return `Future(Some(email))` if there's a logged in user,
    *         `Future(None)` if not
    */
  def loggedInEmail[T](request: Request[T]): Future[Option[String]] = {
    val res = for { userOpt <- loggedInUser(request) }
              yield userOpt
    res map { userOpt => userOpt.map(_.email) }
  }

  /** Determine who's logged in, taking session expiry, IP addresses, etc.,
    * into account.
    *
    * @param request current request
    * @tparam T      request type (not used)
    *
    * @return `Future(Some(user))` if there's a logged in user,
    *         `Future(None)` if not
    */
  def loggedInUser[T](request: Request[T]): Future[Option[User]] = {
    for { dataOpt <- getSession(request)
          data    <- dataOpt.toFuture("No session for request")
          userOpt <- DAO.userDAO.findByEmail(data.userIdentifier) }
    yield userOpt
  }

  /** Clear session data.
    *
    * @param sessionID  the session ID
    */
  def clearSessionData(sessionID: String): Future[Boolean] = {
    sessionStore.clearSessionData(sessionID)
  }

  // --------------------------------------------------------------------------
  // Private methods
  // --------------------------------------------------------------------------

  private def getSession[T](request: Request[T]):
    Future[Option[SessionData]] = {

    request.session.get(SessionKey).map { sessionID =>
      for { dataOpt        <- sessionStore.getSessionData(sessionID)
            data           <- dataOpt.toFuture("No session.")
            checkedDataOpt <- checkSession(request, data) }
        yield checkedDataOpt
    }
    .getOrElse(Future.successful(None))
  }

  // Determine if a session is valid.
  private def checkSession[T](request: Request[T], sessionData: SessionData):
    Future[Option[SessionData]] = {

    val now = DateTime.now
    val res = if (DateTime.now isAfter sessionData.validUntil) {
      failedFuture(s"Session ${sessionData.sessionID} " +
                   s"(${sessionData.userIdentifier}) has expired.")
    }
    else if (request.remoteAddress != sessionData.ipAddress) {
      failedFuture(s"Session IP address ${sessionData.ipAddress} doesn't " +
                   s"match request IP address ${request.remoteAddress}")
    }
    else {
      logger.debug { s"Refreshing session ${sessionData.sessionID} for " +
                     s"${sessionData.userIdentifier}. Session now expires " +
                     s"at ${sessionData.validUntil}" }
      sessionStore.refresh(sessionData)
    }

    res map { data =>
      Some(data)
    } recoverWith {
      case NonFatal(e) => {
        logger.error(s"Error with session ${sessionData.sessionID} for " +
                     s"(${sessionData.userIdentifier})", e)
        logger.debug { s"Clearing session ${sessionData.sessionID}" }
        clearSessionData(sessionData.sessionID)
        Future.successful(None)
      }
    }
  }
}
