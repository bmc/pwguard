package controllers

import dbservice.DAO
import models.User
import play.api._
import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.Play.current
import play.api.libs.json.{ JsString, Json, JsValue }

import services.Logging

import scala.concurrent.Future

/** Base class for all controllers.
  */
trait BaseController extends Logging {

  val logger = pwguard.global.Globals.mainLogger

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  /** Add appropriate "no cache" headers to a result.
    *
    * @param result the result to annotate
    *
    * @return the modified result
    */
  protected def noCache(result: Result): Result = {
    result.withHeaders("Pragma" -> "no-cache", "Cache-Control" -> "no-cache")
  }

  /** Modify a result to honor the configured caching directive.
    *
    * @param result the result to check and possibly modify
    *
    * @return the possibly modified result
    */
  protected def maybeCached(result: Result): Result = {
    val cache = current.configuration
                       .getBoolean("http.cacheStaticResources")
                       .getOrElse(false)
    if (cache) result else noCache(result)
  }

  /** Convenience method to process incoming secured JSON request, sending
    * back a consistent error when no user is logged in. Built on top of
    * `ActionWithUser`.
    *
    * @param f   The handler returning the JSON result, wrapped in a Future
    * @return    The actual action
    */
  def UnsecuredJSONAction(f: (Request[JsValue]) => Future[Result]) = {
    (CheckSSLAction andThen
     Action andThen
     LoggedAction).async(parse.json) { req =>
      f(req)
    }
  }

  /** Convenience method to processing incoming secured request, sending
    * back a consistent error when no user is logged in. Built on top of
    * `ActionWithUser`.
    *
    * @param f   The handler returning the result, wrapped in a Future
    * @return    The actual action
    */
  def UnsecuredAction(f: (Request[AnyContent]) => Future[Result]) = {
    (CheckSSLAction andThen Action andThen LoggedAction).async { req =>
      f(req)
    }
  }

  /** Convenience method to process incoming secured JSON request, sending
    * back a consistent error when no user is logged in. Built on top of
    * `ActionWithUser`.
    *
    * @param f   The handler returning the JSON result, wrapped in a Future
    * @return    The actual action
    */
  def SecuredJSONAction(f: (AuthenticatedRequest[JsValue]) => Future[Result]) = {
    (CheckSSLAction andThen
     LoggedAction andThen
     AuthenticatedAction).async(parse.json) { authReq =>
      f(authReq)
    }
  }

  /** Convenience method to processing incoming secured request, sending
    * back a consistent error when no user is logged in. Built on top of
    * `ActionWithUser`.
    *
    * @param f   The handler returning the result, wrapped in a Future
    * @return    The actual action
    */
  def SecuredAction(f: (AuthenticatedRequest[AnyContent]) => Future[Result]) = {
    (CheckSSLAction andThen
     LoggedAction andThen
     AuthenticatedAction).async { authReq =>
      f(authReq)
    }
  }

  /** Convenience method to processing incoming secured request, sending
    * back a consistent error when no user is logged in. Built on top of
    * `ActionWithUser`.
    *
    * @param parser  The desired body parser
    * @param f       The handler returning the result, wrapped in a Future
    * @return        The actual action
    */
  def SecuredAction[T](parser: BodyParser[T])
                   (f: (AuthenticatedRequest[T]) => Future[Result]) = {
    (CheckSSLAction andThen
     LoggedAction andThen
     AuthenticatedAction).async(parser) { authReq =>
      f(authReq)
    }
  }

  /** Parameters to pass back in a new session.
    *
    * @param user the current user
    *
    * @return a sequence of pairs
    */
  protected def sessionParameters(user: User): Seq[(String, String)]= {
    Seq(Security.username -> user.email)
  }

  /** Generate consistent JSON error output.
    *
    * @param error       optional error message
    * @param status      optional HTTP status
    * @param fieldErrors option form field errors, keyed by field ID
    *
    * @return the JSON result
    */
  protected def jsonError(error:       Option[String],
                          status:      Option[Int],
                          fieldErrors: (String, String)*): JsValue = {

    val emptyJsonObject  = Map.empty[String, JsValue]
    val fieldErrorJson   = if (fieldErrors.length > 0)
                             Map("fields" -> Json.toJson(Map(fieldErrors: _*)))
                           else
                             emptyJsonObject
    val errorMessageJson = error.map { s => Map("message" -> JsString(s)) }.
                                 getOrElse(emptyJsonObject)

    val json = Json.obj("error" -> (fieldErrorJson ++ errorMessageJson))
    status.map { i => json ++ Json.obj("status" -> i) }.getOrElse(json)
  }

  /** Alternate version of `jsonError` without a status code.
    *
    * @param error        Error string
    * @param fieldErrors option form field errors, keyed by field ID
    *
    * @return the JSON result
    */
  protected def jsonError(error:       String,
                          fieldErrors: (String, String)*): JsValue = {
    jsonError(Some(error), None, fieldErrors: _*)
  }

  /** Alternate version of `jsonError` taking an exception.
    *
    * @param message     A message prefix
    * @param exception   The exception
    * @param status      HTTP status
    *
    * @return the JSON result
    */
  protected def jsonError(message:   String,
                          exception: Throwable,
                          status:    Option[Int] = None): JsValue = {
    val clsName = exception.getClass.getName.split("""\.""").last
    val msg = Option(exception.getMessage).map {
      s => s"$clsName: $message ($s)"
    }.
    getOrElse(clsName)

    jsonError(Some(msg), status)
  }

  /** Alternate version of `jsonError` taking an exception.
    *
    * @param exception   The exception
    * @param status      HTTP status
    *
    * @return the JSON result
    */
  protected def jsonError(exception: Throwable): JsValue = {
    val clsName = exception.getClass.getName.split("""\.""").last
    val msg = Option(exception.getMessage).map { message =>
      s"$clsName: $message"
    }.
    getOrElse(clsName)

    jsonError(msg)
  }
}
