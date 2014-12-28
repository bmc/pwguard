package controllers

import play.api._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Play.current
import pwguard.global.Globals
import pwguard.global.Globals.ExecutionContexts.Default._
import util.UserAgent.UserAgent
import util.EitherOptionHelpers._

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Success, Try}

/** Main controller.
  */
object MainController extends BaseController {

  override val logger = Logger("pwguard.controllers.MainController")

  private val UserAgentService = Globals.UserAgentDecoderService

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def index(isMobile: Option[String]) = UnsecuredAction { implicit request =>
      val browserLogLevel = current.configuration.getString("browserLoggingLevel")
                                                 .getOrElse("error")

      def isMobileBrowser(): Future[Boolean] = {
        // Did a parameter come in on the request? If so, use it to override
        // the User-Agent setting.
        val f = Future {
          isMobile map { _.toBoolean }
        } recover {
          case NonFatal(e) => noneT[Boolean]
        }

        f flatMap { mobileParamOpt: Option[Boolean] =>
          mobileParamOpt map { v =>
            // Use the parameter.
            Future.successful(v)
          } getOrElse {
            // Retrieve the user agent information.
            getUserAgent(request).map { userAgent: UserAgent =>
              userAgent.isMobile
            }
          }
        }
      }

      isMobileBrowser() map { isMobile =>
        Ok(views.html.index(browserLogLevel, isMobile))
      } recover {
      // If an error occurred, log it, but assume false.
      case NonFatal(e) => {
        logger.error("Error determining whether browser is mobile", e)
        BadRequest(jsonError("Error determining whether browser is mobile", e))
      }
    }
  }

  /** Static handler, for delivering static files during development. Should
    * be subsumed by a static handler in the front-end web server, for
    * development.
    */
  def static(path: String) = UnsecuredAction { implicit request =>

    // Search both "static" and "bower_components".
    Future {
      val files = Seq(s"static/bower/$path",
                      s"static/$path",
                      s"static/javascripts/$path")
        .map { Play.getFile(_) }
        .dropWhile { f => ! (f.exists) }
        .take(1)

      files match {
        case Nil       => NotFound
        case f :: tail => maybeCached(Ok.sendFile(content = f, inline = true))
      }
    }
  }

  /** Retrieve an Angular.js template. Most of the time, these templates
    * could be static. However, every so often, it's useful to fill something
    * in on the backend first. So, we run each one through Play's templating
    * mechanism first. These templates should _not_ take parameters.
    *
    * NOTE: Template names should be specified as `foo.html`, not as
    * `foo.scala.html`, in the Angular.js code. Angular doesn't need to know
    * whether the backend template is being processed by Scala.
    *
    * This action first attempts to find a static template. If a static
    * template is not available, the action attempts to find a Play
    * template class.
    *
    * @param name  the template name.
    */
  def getAngularTemplate(name: String) = UnsecuredAction { implicit request =>

    Future {
      val file = Play.getFile(s"static/AngularTemplates/$name")
      val result = if (file.exists) {
        Success(maybeCached(Ok.sendFile(content = file, inline = true)))
      }

      else {
        // Static file doesn't exist. Try loading and processing as a Play
        // template.

        findAngularPlayTemplate(name)
      }

      result.recover {
        case e: ClassNotFoundException =>
          NotFound

        case e: Exception => {
          logger.error(s"Error loading template for $name", e)
          InternalServerError
        }
      }.get
    }
  }

  /** Get decoded information about the user agent.
    */
  def getUserAgentInfo = UnsecuredAction { implicit request =>

    getUserAgent(request).map { userAgent: UserAgent =>
      Ok(Json.obj("userAgentInfo" -> Json.obj("isMobile" -> userAgent.isMobile)))
    }
    .recover { case _ =>
      Ok(jsonError("Unable to get information about your browser."))
    }
  }

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

  private def getUserAgent[T](request: Request[T]): Future[UserAgent] = {

    val userAgent = request.headers.get("User-Agent").getOrElse("")

    UserAgentService.decodeUserAgent(userAgent)
  }

  private def findAngularPlayTemplate(name: String): Try[Result] = {
    import grizzled.file.util

    val (dirname, basename, extension) = util.dirnameBasenameExtension(name)
    val parent = dirname.replaceAllLiterally(".", "").trim()

    val className = if (parent.length > 0)
      s"views.html.AngularTemplates.$parent.$basename"
    else
      s"views.html.AngularTemplates.$basename"

    Try {
      val templateClass = Class.forName(className)
      val applyMethod = templateClass.getMethod("apply")
      applyMethod.invoke(null) match {
        case html: play.twirl.api.HtmlFormat.Appendable =>
          Ok(html)

        case r: Any => {
          throw new Exception(s"unexpected ${r.getClass} type from template")
        }
      }
    }
  }
}
