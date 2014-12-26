package controllers

import dbservice.DAO
import models.User
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Play.current
import play.api.libs.json.{ JsString, Json, JsValue }

import pwguard.global.Globals.ExecutionContexts.Default._
import util.EitherOptionHelpers.Implicits._

import scala.concurrent.Future
import scala.util.control.NonFatal

/** Base class for all controllers.
  */
trait BaseController {

  protected val logger = pwguard.global.Globals.mainLogger

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

    if (cache)
      result
    else
      noCache(result)
  }

  /** `Action` wrapper for actions that do not require a logged-in user.
    * Play infers the body parser to use from the incoming HTTP headers.
    *
    * @param f  the caller's block of action code
    */
  protected def UnsecuredAction(f: Request[AnyContent] => Future[Result]) = {
    Action.async { implicit request =>
      logAndHandleRequest(f, request)
    }
  }

  /** `Action` wrapper for actions that do not require a logged-in user.
    * Caller specifies the desired body parser type; requests that don't
    * adhere to that type are rejected by Play.
    *
    * @param bodyParser specific body parser to use
    * @tparam T         body parser type
    * @return           the Action
    */
  protected def UnsecuredAction[T](bodyParser: BodyParser[T])
                                  (f: Request[T] => Future[Result]) = {
    Action.async(bodyParser) { implicit request =>
      logAndHandleRequest(f, request)
    }
  }

  /** `Action` wrapper for actions that require a logged-in user. Play infers
    * the parser to use from the incoming content type. Example use:
    *
    * {{{
    * def doAmazingAction = ActionWithUser(
    *   { (user, request) => Future(amazing(user, request)) },
    *   { request         => Future(Redirect(routes.Application.login())) }
    * )
    * }}}
    *
    * @param whenLoggedIn function to call if a user is logged in; must return
    *               a `Future[SimpleResult]`
    * @param noUser       function to call if there isn't a logged-in user;
    *               must return a `Future[SimpleResult]`
    * @return the actual action
    */
  def ActionWithUser(whenLoggedIn: (User, Request[AnyContent]) => Future[Result],
                     noUser:       Request[AnyContent] => Future[Result]):
    Action[AnyContent] = {

    ActionWithUser(BodyParsers.parse.anyContent)(whenLoggedIn, noUser)
  }

  /** `Action` wrapper for actions that require a logged-in user. Uses an
    * explicit body parser. Example use:
    *
    * {{{
    * def doAmazingAction = ActionWithUser(parse.json) {
    * { (user, request) => Future(amazingJson(user, request)) },
    * { request         => Future(errorJson("not logged in) }
    * )
    * }}}
    *
    * @param bodyParser   the body parser to use
    * @param whenLoggedIn function to call if a user is logged in; must return
    *               a `Future[SimpleResult]`
    * @param noUser       function to call if there isn't a logged-in user;
    *               must return a `Future[SimpleResult]`
    * @return the actual action
    */
  protected def ActionWithUser[T](bodyParser: BodyParser[T])
                                 (whenLoggedIn: (User, Request[T]) => Future[Result],
                                  noUser:       Request[T] => Future[Result]): Action[T] = {

    Action.async(bodyParser) { implicit request =>

      SessionOps.loggedInEmail(request).flatMap { emailOpt =>
        emailOpt.map { email =>
          def handle(user: User): Future[Result] = {
            def fwd(req: Request[T]): Future[Result] = whenLoggedIn(user, req)
            logAndHandleRequest(fwd, request)
          }

          val f = for { optUser <- DAO.userDAO.findByEmail(email)
                        user    <- optUser.toFuture(s"No user with email $email")
                        res     <- handle(user) }
                  yield res

          f recoverWith {
            case NonFatal(e) => logAndHandleRequest(noUser, request)
          }
        }.
        getOrElse { logAndHandleRequest(noUser, request) }
      }
    }
  }

  /** Convenience method to process incoming secured JSON request, sending
    * back a consistent error when no user is logged in. Built on top of
    * `ActionWithUser`.
    *
    * @param f   The handler returning the JSON result, wrapped in a Future
    * @return    The actual action
    */
  def SecuredJSONAction(f: (User, Request[JsValue]) => Future[Result]) = {
    ActionWithUser(BodyParsers.parse.json)(
      f,

      { request => Future { Unauthorized } }
    )
  }

  /** Convenience method to processing incoming secured request, sending
    * back a consistent error when no user is logged in. Built on top of
    * `ActionWithUser`.
    *
    * @param f   The handler returning the result, wrapped in a Future
    * @return    The actual action
    */
  def SecuredAction(f: (User, Request[AnyContent]) => Future[Result]) = {
    ActionWithUser(
      f,

      { request => Future { Unauthorized } }
    )
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

  // --------------------------------------------------------------------------
  // Protected methods
  // ------------------------------------------------------------------------

  private def logAndHandleRequest[T](handler: Request[T] => Future[Result],
                                     request: Request[T]): Future[Result] = {
    def futureLog(msg: => String): Future[Unit] = {
      Future { logger.debug(msg) }
    }

    // Chained futures ensure that the logging occurs in the right order.
    for { f1 <- futureLog { s"Received request ${request}" }
          res <- handler(request)
          f2  <- futureLog { s"Finished processing request ${request}" } }
    yield res
  }
}
