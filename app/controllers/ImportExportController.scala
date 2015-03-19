package controllers

import models.User
import play.api.mvc.Result
import services.{ImportExportFormat, UploadedFile, ImportExportService, ImportFieldMapping}

import java.io._

import play.api._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.cache.Cache
import play.api.Play.current

import grizzled.file.{util => fileutil}

import pwguard.global.Globals.ExecutionContexts.Default._
import exceptions._
import util.FileHelpers
import util.FutureHelpers._
import util.JsonHelpers.angularJson

import scala.concurrent.Future
import scala.util.control.NonFatal

object ImportExportController extends BaseController {

  override val logger = Logger("pwguard.controllers.ImportExportController")

  import ImportExportService._
  import play.api.mvc.BodyParsers.parse

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
        logger.error("Export failed", e);
        BadRequest(jsonError("Export failed"))
    }
  }

  def importDataUpload = SecuredAction(parse.temporaryFile) { authReq =>

    implicit val request = authReq.request

    // If the upload is a spreadsheet, then we have to allow the user to map
    // the columns. If it's an XML file, then we can import it right away,
    // provided it's one of our XML files.

    FileHelpers.createPseudoTempFile("import", ".dat") map { file =>
      fileutil.copyFile(request.body.file, file)
      for { name <- request.headers.get("X-File-Name")
            mime <- request.headers.get("Content-Type") }
      yield UploadedFile(name, file, mime)

    } flatMap { optData =>
      optData map { uploaded =>
        if (uploaded.fileFormat == ImportExportFormat.XML)
          importXML(uploaded, authReq.user)
        else
          handleUploadedSpreadsheet(uploaded)
      } getOrElse {
        Future.failed(new UploadFailed("Incomplete file upload."))
      }

    } recover {
      case NonFatal(e) => {
        logger.error("Error preparing import", e)
        angularJson(BadRequest, jsonError(e))
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
      successJson(total)

    } recover {
      case NonFatal(e) => {
        logger.error("Cannot complete import", e)
        angularJson(BadRequest, jsonError(e))
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

  private def successJson(totalImported: Int): Result = {
    angularJson(Ok, Json.obj("total" -> totalImported))
  }

  private def handleUploadedSpreadsheet(uploaded: UploadedFile): Future[Result] = {

    for { (f, headers)  <- mapUploadedImportFile(uploaded) }
    yield {
      val opt = headers map { h =>
        Cache.set(FileCacheKey, f.getPath)
        val js = Json.obj(
          "headers" -> h,
          "fields"  -> ImportFieldMapping.values.map { field =>
            Json.obj("name"     -> field.toString,
              "required" -> ImportFieldMapping.isRequired(field))
          }
        )
        angularJson(Ok, js)
      }

      opt.getOrElse {
        throw new UploadFailed("Empty file.")
      }
    }
  }

  private def importXML(uploaded: UploadedFile, user: User): Future[Result] = {
    ImportExportService.importXML(uploaded.file, user) map { n =>
      successJson(n)
    }
  }

}
