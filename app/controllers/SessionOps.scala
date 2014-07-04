package controllers

import dbservice.DAO
import models.User
import org.joda.time.{DateTime, Duration}
import play.api.Logger
import play.api.mvc.Request
import util.session._


/** Session-related stuff.
  */
object SessionOps {

  private val sessionStore   = new MemorySessionStore
  private val SessionTimeout = Duration.standardHours(1)
  private val logger         = pwguard.global.Globals.mainLogger

  val SessionKey = "PWGuard-Session-ID"

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
    val res =
      for {
        dataOpt <- getSession(request).right
        data    <- toRight(dataOpt, "No existing session").right
        data2   <- checkSession(request, data).right }
      yield {
        data2
      }

    toOpt(res)
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
        data    <- toRight(dataOpt, "").right
        userOpt <- DAO.userDAO.findByEmail(data.userIdentifier).right
        user    <- toRight(userOpt, "User ${data.userIdentifier} not found").right
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
      sessionStore.getSessionData(sessionID)
    }
    .getOrElse(Right(None))
  }

  // Determine if a session is valid.
  private def checkSession[T](request: Request[T], sessionData: SessionData):
    Either[String, SessionData] = {

    val now = DateTime.now
    if (DateTime.now isAfter sessionData.validUntil) {
      Left(s"Session ${sessionData.sessionID} (${sessionData.userIdentifier} " +
           "has expired.")
    }
    else if (request.remoteAddress != sessionData.ipAddress) {
      Left(s"Session IP address ${sessionData.ipAddress} doesn't match " +
           s"request IP address ${request.remoteAddress}")
    }
    else {
      Right(sessionData)
    }
  }

  private def toOpt[T](e: Either[String, T]): Option[T] = {
    e match {
      case Left(error) => {
        if (error != "")
          logger.warn(error)
        None
      }
      case Right(t) =>
        Some(t)
    }
  }

  // Adapted from http://proofbyexample.com/combining-option-and-either-in-scala.html
  private def toRight[T](opt: Option[T], orElse: => String):
    Either[String, T] = {

    opt.map { t => Right(t) } getOrElse Left(orElse)
  }
}
