package controllers

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current

import scala.concurrent.Future

/** Main controller.
  */
object MainController extends BaseController {

  override protected val logger = Logger("controllers.MainController")

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
    * that the backend template is being processed by Scala.
    *
    * @param name  the template name.
    */
  def getAngularTemplate(name: String) = Action.async {
    Future {
      import grizzled.file.util

      val file = Play.getFile(s"static/AngularTemplates/$name")
      if (file.exists) {
        Ok.sendFile(content = file, inline = true)
      }

      else {
        // Static file doesn't exist. Try loading and processing as a Play
        // template.

        val (dirname, basename, extension) = util.dirnameBasenameExtension(name)
        val parent = dirname.replaceAllLiterally(".", "").trim()

        val className = if (parent.length > 0)
          s"views.html.AngularTemplates.$parent.$basename"
        else
          s"views.html.AngularTemplates.$basename"

        try {
          val templateClass = Class.forName(className)
          val applyMethod = templateClass.getMethod("apply")
          applyMethod.invoke(null) match {
            case html: play.api.templates.HtmlFormat.Appendable =>
              Ok(html)

            case r: Any => {
              logger.error(s"unexpected ${r.getClass} type from template")
              InternalServerError
            }
          }
        }

        catch {
          case e: Exception => {
            logger.error(s"No static or dynamic template for $name", e)
            NotFound
          }
        }
      }
    }
  }
}
