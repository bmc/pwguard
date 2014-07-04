package controllers

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current

import scala.concurrent.Future
import scala.util.{Success, Try}

/** Main controller.
  */
object MainController extends BaseController {

  override protected val logger = Logger("pwguard.controllers.MainController")

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def index = Action {
    Ok(views.html.index())
  }

  /** Static handler, for delivering static files during development. Should
    * be subsumed by a static handler in the front-end web server, for
    * development.
    */
  def static(path: String) = Action.async {
    sendFile(s"static/$path")
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
  def getAngularTemplate(name: String) = Action.async {
    Future {
      val file = Play.getFile(s"static/AngularTemplates/$name")
      val result = if (file.exists) {
        Success(Ok.sendFile(content = file, inline = true))
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

  // -------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------

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
