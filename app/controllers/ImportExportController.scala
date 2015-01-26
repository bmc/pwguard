package controllers

import akka.actor.Props
import dbservice.DAO
import models.{UserHelpers, User, PasswordEntry}
import org.apache.poi.ss.usermodel.Sheet
import play.api.libs.concurrent.Akka
import services.{UploadedFile, ImportExportFormat, ImportExportService}
import util.EitherOptionHelpers._

import com.github.tototoshi.csv._

import grizzled.io.util._

import java.io._

import play.api._
import play.api.libs.json.{JsString, Json, JsValue}
import play.api.mvc._
import play.api.mvc.Results._
import play.api.cache.Cache
import play.api.Play.current

import pwguard.global.Globals.ExecutionContexts.Default._
import actors.{ImportFieldMapping, ImportData, ImportActor}
import exceptions._
import util.FutureHelpers._

import scala.concurrent.{Promise, Future}
import scala.util.control.NonFatal

object ImportExportController extends BaseController {

  override val logger = Logger("pwguard.controllers.ImportExportController")

  import ImportExportService._

  val XSLXContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  val CSVContentType  = "text/csv"

  private val FileCacheKey = "uploaded-file"

  private val BaseExportFilename = "passwords"

  // -------------------------------------------------------------------------
  // Public methods
  // -------------------------------------------------------------------------

  def exportData(formatString: String) = SecuredAction { authReq =>

    val r = for { format       <- tryToFuture(mapFormatString(formatString))
                  (file, mime) <- createExportFile(authReq.user, format) }
    yield {

      val filename = s"${BaseExportFilename}.${format}"
      Ok.sendFile(file)
        .as(mime)
        .withHeaders("Content-disposition" -> s"attachment; filename=$filename")
    }

    r recover {
      case NonFatal(e) =>
        BadRequest(jsonError(e))
    }
  }

  def importDataUpload = SecuredJSONAction { authReq =>
    implicit val request = authReq.request

    val json = request.body

    val optData = for { name <- (json \ "filename").asOpt[String]
                        data <- (json \ "contents").asOpt[String]
                        mime <- (json \ "mimeType").asOpt[String] }
                  yield UploadedFile(name, data, mime)

    optData map { uploaded =>

      for { (f, headers)  <- mapUploadedImportFile(uploaded) }
      yield {
        val opt = headers map { h =>
          Cache.set(FileCacheKey, f.getPath)
          Ok(
            Json.obj(
              "headers" -> h,
              "fields"  -> ImportFieldMapping.values.map { field =>
                Json.obj("name"     -> field.toString,
                         "required" -> ImportFieldMapping.isRequired(field))
              }
            )
          )
        }

        opt.getOrElse {
          throw new UploadFailed("Empty file.")
        }
      }

    } getOrElse {
      Future.failed(new UploadFailed("Incomplete file upload."))

    } recover {
      case NonFatal(e) => {
        logger.error("Error preparing import", e)
        BadRequest(jsonError(e))
      }
    }
  }

  def completeImport = SecuredJSONAction { authReq =>
    def getFile(): Future[File] = {
      Future {
        val path = Cache.getAs[String](FileCacheKey).getOrElse {
          throw new ImportFailed("No previously uploaded file.")
        }
        val f = new File(path)
        f
      }
    }

    def getMappings(): Future[Map[String, String]] = {
      Future {
        val json = authReq.request.body
        (json \ "mappings").asOpt[Map[String, String]].getOrElse {
          throw new ImportFailed("No mappings.")
        }
      }
    }

    val f = for { file     <- getFile()
                  mappings <- getMappings()
                  total    <- importCSV(file, mappings, authReq.user) }
            yield total


    f map  { total =>
      // Get rid of the entries that weren't saved because they weren't
      // new.
      Ok(Json.obj("total" -> total))

    } recover {
      case NonFatal(e) => {
        logger.error("Cannot complete import", e)
        BadRequest(jsonError(e))
      }
    } andThen {
      case _ => {
        getFile() map { f =>
          f.delete()
          Cache.remove(FileCacheKey)
        }
      }
    }
  }

  // -------------------------------------------------------------------------
  // Private Methods
  // -------------------------------------------------------------------------
}
