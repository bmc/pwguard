package controllers

import dbservice.DAO
import models.User
import org.joda.time.{DateTime, Duration => JodaDuration}
import play.api.Play.current
import play.api.Logger
import play.api.mvc.Request
import util.session._
import util.DateHelpers.Implicits._
import util.EitherOptionHelpers.Implicits._

/** Session-related stuff.
  */
object SessionOps {

  private val sessionStore   = new MemorySessionStore
  private val logger         = Logger("pwguard.controllers.SessionOps")
  private val SessionTimeout = current.configuration
                                      .getMilliseconds("session.timeout")
                                      .orElse { Some(60 * 60 * 1000L) }
                                      .map { JodaDuration.millis(_) }
                                      .get

  val SessionKey  = "PWGuard-Session-ID"
  val IsMobileKey = "PWGuard-Is-Mobile"

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
    Either[String, SessionData] = {

    val sessionData = SessionUtil.newSessionData(
      userIdentifier = user.email,
      ipAddress      = request.remoteAddress,
      duration       = SessionTimeout
    )

    sessionStore.storeSessionData(sessionData).right.map { _ =>
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
  def currentSessionData[T](request: Request[T]): Option[SessionData] = {
    val res = for { dataOpt   <- getSession(request).right
                    data      <- dataOpt.toEither("No existing session").right }
              yield data

    res.toOption
  }

  /** Determine who's logged in, taking session expiry, IP addresses, etc.,
    * into account.
    *
    * @param request current request
    * @tparam T      request type (not used)
    *
    * @return `Some(email)` or `None`
    */
  def loggedInEmail[T](request: Request[T]): Option[String] = {
    val res =
      for {
        dataOpt <- getSession(request).right
        data    <- dataOpt.toEither("").right
        userOpt <- DAO.userDAO.findByEmail(data.userIdentifier).right
        user    <- userOpt.toEither("User ${data.userIdentifier} not found").right
      }
      yield user

    toOpt(res) map { user => user.email }
  }

  /** Clear session data.
    *
    * @param sessionID  the session ID
    */
  def clearSessionData(sessionID: String) {
    sessionStore.clearSessionData(sessionID)
  }

  // --------------------------------------------------------------------------
  // Private methods
  // --------------------------------------------------------------------------

  private def getSession[T](request: Request[T]):
    Either[String, Option[SessionData]] = {

    request.session.get(SessionKey).map { sessionID =>

      for { dataOpt   <- sessionStore.getSessionData(sessionID).right
            data      <- dataOpt.toEither("No session.").right
            validData <- checkSession(request, data).right }
        yield Some(validData)
    }
    .getOrElse(Right(None))
  }

  // Determine if a session is valid.
  private def checkSession[T](request: Request[T], sessionData: SessionData):
    Either[String, SessionData] = {

    val now = DateTime.now
    val res = if (DateTime.now isAfter sessionData.validUntil) {
      Left(s"Session ${sessionData.sessionID} " +
           s"(${sessionData.userIdentifier}) has expired.")
    }
    else if (request.remoteAddress != sessionData.ipAddress) {
      Left(s"Session IP address ${sessionData.ipAddress} doesn't match " +
           s"request IP address ${request.remoteAddress}")
    }
    else {
      logger.debug { s"Refreshing session ${sessionData.sessionID} for " +
                     s"${sessionData.userIdentifier}" }
      sessionStore.refresh(sessionData)
    }

    res match {
      case Left(error) => {
        logger.error(s"Error with session ${sessionData.sessionID} for " +
                     s"(${sessionData.userIdentifier}): $error")
        logger.debug { s"Clearing session ${sessionData.sessionID}" }
        clearSessionData(sessionData.sessionID)
      }

      case Right(newData) => {
        logger.debug {
          s"Session ${newData.sessionID} now expires at ${newData.validUntil}"
        }
      }
    }

    res
  }

  private def toOpt[T](e: Either[String, T]): Option[T] = {

    e.left.map {
      error => if (error != "") logger.warn(error)
      error
    }.
    toOption
  }
}
